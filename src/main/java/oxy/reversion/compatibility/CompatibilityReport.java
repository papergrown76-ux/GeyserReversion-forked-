package oxy.reversion.compatibility;

public record CompatibilityReport(String version, String build, State state, boolean allowed, String reason) {
    public enum State { TESTED, UNTESTED_BUILD, UNSUPPORTED, UNKNOWN }
    public static CompatibilityReport evaluate(String version, String build, boolean allowUntested) {
        if (version == null || version.isBlank() || version.equals("unknown")) {
            return new CompatibilityReport("unknown", build, State.UNKNOWN, allowUntested, "Cannot identify Geyser version");
        }
        if (version.matches("2\\.10\\.0(?:[-+].*)?")) {
            boolean tested = "1162".equals(build);
            return new CompatibilityReport(version, build, tested ? State.TESTED : State.UNTESTED_BUILD, true,
                    tested ? "Validated with Geyser 2.10.0 build 1162" : "Geyser 2.10.0 API family; this build has not been validated");
        }
        return new CompatibilityReport(version, build, State.UNSUPPORTED, allowUntested,
                "Requires Geyser 2.10.0; detected a different internal API version");
    }
    public static CompatibilityReport detect(Object host, boolean allowUntested) {
        try {
            return evaluate(String.valueOf(host.getClass().getField("VERSION").get(null)),
                    String.valueOf(host.getClass().getField("BUILD_NUMBER").get(null)), allowUntested);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return evaluate("unknown", "unknown", allowUntested);
        }
    }
}
