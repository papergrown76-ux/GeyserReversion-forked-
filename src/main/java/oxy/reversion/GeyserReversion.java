package oxy.reversion;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.reflect.Agents;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import oxy.reversion.util.ClassLoaderPriorityUtil;
import oxy.reversion.util.ServerUtil;
import oxy.reversion.config.Config;
import oxy.reversion.config.ConfigLoader;
import oxy.reversion.util.auth.PendingBedrockAuthentication;
import oxy.reversion.util.protocol.CodecUtil;
import oxy.reversion.util.transformers.BaseBedrockCodecHelperTransformer;
import oxy.reversion.util.transformers.GeyserExtensionClassProvider;
import oxy.reversion.util.transformers.OuranosItemTransformer;
import oxy.reversion.util.transformers.OuranosCodecTransformer;
import oxy.reversion.util.transformers.OuranosBlockDictionaryTransformer;
import oxy.reversion.util.transformers.OuranosBlockStateTransformer;
import oxy.reversion.util.transformers.OuranosFallbackTransformer;
import oxy.reversion.compatibility.CompatibilityReport;
import oxy.reversion.diagnostics.StatusReport;
import oxy.reversion.diagnostics.TranslationDiagnostics;
import oxy.reversion.user.ReversionUser;
import oxy.reversion.util.protocol.ProtocolSet;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.registry.BlockRegistries;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class GeyserReversion implements Extension {
    private static ExtensionLogger LOGGER;
    public static ExtensionLogger extensionlogger() {
        return LOGGER;
    }

    private static boolean supportV575AndBelow = true;
    public static boolean doWeSupportBelow575() {
        return supportV575AndBelow;
    }

    private static Config CONFIG = new Config();
    private static final Map<GeyserConnection, ReversionUser> USERS = new ConcurrentHashMap<>();
    private boolean bridgeReady;
    private CompatibilityReport compatibility = CompatibilityReport.evaluate("unknown", "unknown", false);
    public static void track(ReversionUser user) { USERS.put(user.getSession(), user); }
    public boolean bridgeReady() { return bridgeReady; }
    public CompatibilityReport compatibility() { return compatibility; }
    @Override
    public String rootCommand() { return "reversion"; }

    @Subscribe
    public void onDisconnect(SessionDisconnectEvent event) { USERS.remove(event.connection()); }

    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        event.register(Command.<CommandSource>builder(this).name("status")
                .description("Show Geyser compatibility, client versions and translation diagnostics")
                .source(CommandSource.class).permission("geyserreversion.command.status")
                .suggestedOpOnly(true).playerOnly(false)
                .executor((source, command, args) -> {
                    if (!source.isConsole() && !source.hasPermission("geyserreversion.command.status")) {
                        source.sendMessage("Missing permission: geyserreversion.command.status");
                        return;
                    }
                    var players = new ArrayList<StatusReport.Player>();
                    USERS.entrySet().removeIf(entry -> entry.getValue().getSession().isClosed());
                    for (var connection : geyserApi().onlineConnections()) {
                        ReversionUser user = USERS.get(connection);
                        int protocol = user == null ? connection.protocolVersion() : user.getProtocolId();
                        var codec = ProtocolSet.getPacketCodec(protocol);
                        players.add(new StatusReport.Player(connection.name(), protocol,
                                codec == null ? "unknown" : codec.getMinecraftVersion(), user != null,
                                user == null ? new TranslationDiagnostics.Snapshot(0, 0, 0, "none") : user.getDiagnostics().snapshot(),
                                user == null ? 0 : user.getFallbacks().configuredReplacements()));
                    }
                    int blocks = bridgeReady ? BlockRegistries.CUSTOM_BLOCKS.get().length : 0;
                    StatusReport.format(compatibility, bridgeReady, CONFIG.fallbacks().enabled(), blocks, players).forEach(source::sendMessage);
                }).build());
    }
    public static Config config() {
        return CONFIG;
    }

    public static BedrockCodec TARGET_CODEC = CodecUtil.rebuildCodec(Bedrock_v944.CODEC);

    public static PendingBedrockAuthentication AUTH = new PendingBedrockAuthentication();

    @Subscribe
    public void onGeyserPreInitializeEvent(GeyserPreInitializeEvent event) {
        LOGGER = logger();
        bridgeReady = false;
        compatibility = CompatibilityReport.detect(geyserApi(), false);
        Config loaded = ConfigLoader.load(this, GeyserReversion.class, Config.class);
        if (loaded == null) {
            LOGGER.error("Protocol bridge disabled: failed to load config.yml. Use /reversion status for diagnostics.");
            return;
        }
        CONFIG = loaded;
        try {
            CONFIG.validate();
        } catch (IllegalArgumentException failure) {
            LOGGER.error("Protocol bridge disabled: " + failure.getMessage());
            return;
        }
        compatibility = CompatibilityReport.detect(geyserApi(), CONFIG.allowUntestedGeyser());
        LOGGER.info("Geyser compatibility: " + compatibility.version() + " build " + compatibility.build()
                + " | " + compatibility.state() + " | " + compatibility.reason());
        if (!compatibility.allowed()) {
            LOGGER.warning("Protocol bridge disabled for this Geyser version. Geyser's native listener remains active.");
            return;
        }
        // Mainly for testing/debugging.
        ClassLoaderPriorityUtil.loadOverridingJars(this);

        try {
            TransformerManager transformerManager = new TransformerManager(new GeyserExtensionClassProvider());
            transformerManager.addTransformer(BaseBedrockCodecHelperTransformer.class.getName());
            transformerManager.addTransformer(OuranosCodecTransformer.class.getName());
            transformerManager.addTransformer(OuranosBlockStateTransformer.class.getName());
            transformerManager.addTransformer(OuranosBlockDictionaryTransformer.class.getName());
            transformerManager.addTransformer(OuranosItemTransformer.class.getName());
            transformerManager.addTransformer(OuranosFallbackTransformer.class.getName());
            transformerManager.hookInstrumentation(Agents.getInstrumentation());
            bridgeReady = true;
        } catch (Exception | LinkageError e) {
            supportV575AndBelow = false;
            LOGGER.error("Protocol bridge disabled: failed to initialize compatibility transformers", e);
        }
    }

    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        if (!bridgeReady) return;
        try {
            // Shutdown Geyser and restart it to use our custom packet handler.
            ServerUtil.restartWithInitializer();
        } catch (Exception e) {
            bridgeReady = false;
            throw new RuntimeException(e);
        }
    }
}
