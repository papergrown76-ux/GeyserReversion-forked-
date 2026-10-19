package oxy.reversion.util.transformers;

import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.session.storage.BlockDictionaryStorage;
import com.github.blackjack200.ouranos.session.storage.OuranosStorage;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CInline;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import oxy.reversion.user.ReversionUser;

@CTransformer(BlockDictionaryStorage.class)
public class OuranosBlockDictionaryTransformer extends OuranosStorage {
    public OuranosBlockDictionaryTransformer(OuranosSession user) {
        super(user);
    }

    @CInline
    @CInject(method = "get", target = @CTarget("HEAD"), cancellable = true)
    public void getServerDictionary(int protocol, InjectionCallback callback) {
        if (user instanceof ReversionUser reversion && protocol == reversion.getTargetVersion()
                && reversion.getServerBlockDictionary() != null) {
            callback.setReturnValue(reversion.getServerBlockDictionary());
        }
    }
}
