package cn.longer233.gamenarrator.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class MobileCapabilityTest {
    @Test public void inventoryUsesAllFiveStatusesAndNeverOverclaims() {
        List<MobileCapability.Capability> capabilities = MobileCapability.current();
        assertFalse(capabilities.isEmpty());
        Set<CapabilityStatus> seen = new HashSet<>();
        for (MobileCapability.Capability capability : capabilities) {
            assertNotNull(capability.name());
            assertNotNull(capability.status());
            assertFalse(capability.status().label().isBlank());
            seen.add(capability.status());
            if (!capability.status().isUsable()) {
                assertFalse("未验证/未实现能力不得声称已完成或已可用：" + capability.name(),
                        capability.detail().contains("已完成") || capability.detail().contains("已可用"));
            }
        }
        assertTrue(seen.contains(CapabilityStatus.AVAILABLE));
        assertTrue(seen.contains(CapabilityStatus.LOCAL_ONLY));
        assertTrue(seen.contains(CapabilityStatus.NOT_IMPLEMENTED));
    }

    @Test public void usabilityMatchesCanonicalStatus() {
        assertTrue(CapabilityStatus.AVAILABLE.isUsable());
        assertTrue(CapabilityStatus.LOCAL_ONLY.isUsable());
        assertTrue(CapabilityStatus.CLOUD_REQUIRED.isUsable());
        assertFalse(CapabilityStatus.NOT_IMPLEMENTED.isUsable());
        assertFalse(CapabilityStatus.DISABLED.isUsable());
    }

    @Test public void disabledEntriesRequireDeviceValidation() {
        for (MobileCapability.Capability capability : MobileCapability.current()) {
            if (capability.status() == CapabilityStatus.DISABLED) {
                assertTrue("禁用能力应标注验证状态：" + capability.name(),
                        capability.detail().contains("待真机") || capability.detail().contains("未接入"));
            }
        }
    }

    @Test public void statusLabelsAreStable() {
        assertEquals("可用", CapabilityStatus.AVAILABLE.label());
        assertEquals("本地可用", CapabilityStatus.LOCAL_ONLY.label());
        assertEquals("需要云端", CapabilityStatus.CLOUD_REQUIRED.label());
        assertEquals("未实现", CapabilityStatus.NOT_IMPLEMENTED.label());
        assertEquals("已禁用", CapabilityStatus.DISABLED.label());
    }

    @Test public void requiredCapabilityDomainsAreTracked() {
        Set<String> names = new HashSet<>();
        for (MobileCapability.Capability capability : MobileCapability.current()) names.add(capability.name());
        assertTrue(names.contains("AI 视觉（画面理解）"));
        assertTrue(names.contains("AI 字幕（自动转写/生成）"));
        assertTrue(names.contains("AI 配音（系统 TTS）"));
        assertTrue(names.contains("转场"));
        assertTrue(names.contains("字幕烧录"));
        assertTrue(names.contains("音频混合"));
        assertTrue(names.contains("平台导入（HTTP/HTTPS 直链）"));
        assertTrue(names.contains("端侧转写"));
    }

    @Test public void modelPresenceFlipsCapabilitiesToLocalOnly() {
        MobileModelDirectory.Presence present = new MobileModelDirectory.Presence(true, true, true);
        Map<String, CapabilityStatus> byName = new HashMap<>();
        for (MobileCapability.Capability capability : MobileCapability.current(present, false, true)) {
            byName.put(capability.name(), capability.status());
        }
        assertEquals(CapabilityStatus.LOCAL_ONLY, byName.get("AI 视觉（画面理解）"));
        assertEquals(CapabilityStatus.LOCAL_ONLY, byName.get("AI 字幕（自动转写/生成）"));
        assertEquals(CapabilityStatus.LOCAL_ONLY, byName.get("端侧 AI 剧情/分镜生成"));
    }

    @Test public void whisperModelWithoutEngineStaysNotImplemented() {
        MobileModelDirectory.Presence present = new MobileModelDirectory.Presence(true, false, false);
        Map<String, CapabilityStatus> withoutEngine = new HashMap<>();
        for (MobileCapability.Capability capability : MobileCapability.current(present, false, false)) {
            withoutEngine.put(capability.name(), capability.status());
        }
        assertEquals(CapabilityStatus.NOT_IMPLEMENTED, withoutEngine.get("AI 字幕（自动转写/生成）"));
        Map<String, CapabilityStatus> withEngine = new HashMap<>();
        for (MobileCapability.Capability capability : MobileCapability.current(present, false, true)) {
            withEngine.put(capability.name(), capability.status());
        }
        assertEquals(CapabilityStatus.LOCAL_ONLY, withEngine.get("AI 字幕（自动转写/生成）"));
    }

    @Test public void systemSpeechRecognizerFlipsOnDeviceTranscription() {
        MobileModelDirectory.Presence none = new MobileModelDirectory.Presence(false, false, false);
        Map<String, CapabilityStatus> available = new HashMap<>();
        for (MobileCapability.Capability capability : MobileCapability.current(none, true)) {
            available.put(capability.name(), capability.status());
        }
        assertEquals(CapabilityStatus.LOCAL_ONLY, available.get("端侧转写"));
        Map<String, CapabilityStatus> unavailable = new HashMap<>();
        for (MobileCapability.Capability capability : MobileCapability.current(none, false)) {
            unavailable.put(capability.name(), capability.status());
        }
        assertEquals(CapabilityStatus.NOT_IMPLEMENTED, unavailable.get("端侧转写"));
    }

    @Test public void deviceValidatedCapabilitiesAreLocalOnly() {
        Map<String, CapabilityStatus> byName = new HashMap<>();
        for (MobileCapability.Capability capability : MobileCapability.current()) {
            byName.put(capability.name(), capability.status());
        }
        assertEquals(CapabilityStatus.LOCAL_ONLY, byName.get("字幕烧录"));
        assertEquals(CapabilityStatus.LOCAL_ONLY, byName.get("转场"));
        assertEquals(CapabilityStatus.LOCAL_ONLY, byName.get("音频混合"));
    }
}
