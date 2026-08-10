package cn.longer233.gamenarrator.mobile;

/**
 * Canonical capability status. UI must not show a capability as usable unless
 * the status actually permits use on this device.
 */
public enum CapabilityStatus {
    AVAILABLE("可用"),
    LOCAL_ONLY("本地可用"),
    CLOUD_REQUIRED("需要云端"),
    NOT_IMPLEMENTED("未实现"),
    DISABLED("已禁用");

    private final String label;

    CapabilityStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isUsable() {
        return this == AVAILABLE || this == LOCAL_ONLY || this == CLOUD_REQUIRED;
    }
}
