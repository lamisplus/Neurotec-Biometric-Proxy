package org.lamisplus.biometric.service;

import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.devices.NFScanner;
import com.neurotec.licensing.NLicense;
import com.neurotec.plugins.NPlugin;
import com.neurotec.plugins.NPluginManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.lamisplus.biometric.config.NeurotecProperties;
import org.lamisplus.biometric.domain.dto.ErrorCodeDTO;
import org.lamisplus.biometric.domain.enumeration.ErrorCode;
import org.lamisplus.biometric.util.LibraryManager;
import org.lamisplus.biometric.util.ReaderMatcher;
import org.lamisplus.biometric.util.Utils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@DependsOn("applicationProperties")
@RequiredArgsConstructor
public class FingerScannerManager {

    private final NeurotecProperties neurotecProperties;

    private NDeviceManager deviceManager;

    @PostConstruct
    public void init() {
        LibraryManager.initLibraryPath();
        if (StringUtils.isBlank(LibraryManager.getLibraryPath())) {
            LOG.error("No Neurotec native library path could be resolved. Set application.library-path in "
                    + "biometric-db-config.yml to the SDK architecture folder, for example "
                    + "C:\\neurotec\\Bin\\Win64_x64. Its parent folder must be named Bin. Without it the SDK "
                    + "cannot load and every biometric operation will fail.");
        }
        obtainLicenses();
        initDeviceManager();
        logDiscoveredDevices();
    }

    private void obtainLicenses() {
        for (String component : neurotecProperties.getLicenseComponents()) {
            try {
                boolean obtained = NLicense.obtainComponents(
                        neurotecProperties.getLicenseServer(),
                        neurotecProperties.getLicensePort(),
                        component);
                if (obtained) {
                    LOG.info("Obtained licence component {}", component);
                } else {
                    LOG.error("Licence component {} was NOT obtained - features depending on it will not work",
                            component);
                }
            } catch (Exception e) {
                LOG.error("Could not obtain licence component {}: {}", component, e.getMessage());
            }
        }
    }

    private void initDeviceManager() {
        try {
            configurePluginSearchPath();
            deviceManager = new NDeviceManager();
            deviceManager.setDeviceTypes(EnumSet.of(NDeviceType.FSCANNER, NDeviceType.FINGER_SCANNER));
            deviceManager.setAutoPlug(true);
            deviceManager.initialize();
        } catch (Exception e) {
            deviceManager = null;
            LOG.error("Could not initialise the Neurotec device manager: {}", e.getMessage(), e);
        }
    }

    private void configurePluginSearchPath() {
        String searchPath = neurotecProperties.getPluginSearchPath();
        if (StringUtils.isBlank(searchPath)) {
            return;
        }
        try {
            NPluginManager pluginManager = NDeviceManager.getPluginManager();
            pluginManager.setPluginSearchPath(searchPath);
            pluginManager.refresh();
            pluginManager.plugAll();
            LOG.info("Device plugin search path set to {}", searchPath);
        } catch (Exception e) {
            LOG.error("Could not set device plugin search path to {}: {}", searchPath, e.getMessage());
        }
    }

    public List<NDevice> getDevices() {
        List<NDevice> devices = new ArrayList<>();
        if (deviceManager == null) {
            return devices;
        }
        try {
            deviceManager.getDevices().forEach(devices::add);
        } catch (Exception e) {
            LOG.error("Could not read the device list: {}", e.getMessage());
        }
        return devices;
    }

    public List<NFScanner> getFingerScanners() {
        List<NFScanner> scanners = new ArrayList<>();
        for (NDevice device : getDevices()) {
            if (device instanceof NFScanner) {
                scanners.add((NFScanner) device);
            }
        }
        return scanners;
    }

    public List<Map<String, Object>> scanEveryDeviceType() {
        List<Map<String, Object>> found = new ArrayList<>();
        NDeviceManager allTypes = null;
        try {
            allTypes = new NDeviceManager();
            allTypes.setDeviceTypes(EnumSet.of(NDeviceType.ANY));
            allTypes.setAutoPlug(true);
            allTypes.initialize();
            for (NDevice device : allTypes.getDevices()) {
                found.add(describe(device));
            }
        } catch (Exception e) {
            LOG.error("Could not enumerate devices of every type: {}", e.getMessage());
        } finally {
            if (allTypes != null) {
                try {
                    allTypes.dispose();
                } catch (Exception e) {
                    LOG.debug("Could not dispose the diagnostic device manager: {}", e.getMessage());
                }
            }
        }
        return found;
    }

    public Optional<NFScanner> resolveScanner(String reader) {
        List<NFScanner> scanners = getFingerScanners();
        if (scanners.isEmpty()) {
            LOG.warn("No finger scanner is visible to the Neurotec device manager (asked for '{}')", reader);
            logPlugins();
            return Optional.empty();
        }

        List<ReaderMatcher.Candidate> candidates = describe(scanners);
        ReaderMatcher.Match match = ReaderMatcher.match(reader, candidates);
        if (match == null) {
            LOG.warn("Reader '{}' matches no attached scanner. Attached: {}", reader, candidates);
            return Optional.empty();
        }

        if (match.getStrategy() != ReaderMatcher.Strategy.EXACT_NAME) {
            LOG.info("Reader '{}' bound to {} by {}", reader, candidates.get(match.getIndex()), match.getStrategy());
        }
        return Optional.of(scanners.get(match.getIndex()));
    }

    public ErrorCodeDTO bootStatus(String reader) {
        boolean attached = resolveScanner(reader).isPresent();
        ErrorCode errorCode = attached ? ErrorCode.SGFDX_ERROR_NONE : ErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND;
        return ErrorCodeDTO.builder()
                .errorID(errorCode.getErrorID())
                .errorName(attached ? errorCode.getErrorName() : ReaderMatcher.decode(reader))
                .errorMessage(errorCode.getErrorMessage())
                .errorType(errorCode.getType())
                .build();
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("workingDirectory", Utils.getWorkingDirectory());
        report.put("derivedLibraryPath", LibraryManager.getLibraryPath());
        report.put("jnaLibraryPath", System.getProperty("jna.library.path"));
        report.put("configuredPluginSearchPath", neurotecProperties.getPluginSearchPath());
        report.put("deviceManagerInitialised", deviceManager != null && deviceManager.isInitialized());

        Map<String, Object> licences = new LinkedHashMap<>();
        for (String component : neurotecProperties.getLicenseComponents()) {
            try {
                licences.put(component, NLicense.isComponentActivated(component));
            } catch (Exception e) {
                licences.put(component, "error: " + e.getMessage());
            }
        }
        report.put("licenceComponents", licences);

        List<Map<String, Object>> plugins = new ArrayList<>();
        try {
            NPluginManager pluginManager = NDeviceManager.getPluginManager();
            report.put("effectivePluginSearchPath", pluginManager.getPluginSearchPath());
            for (NPlugin plugin : pluginManager.getPlugins()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("file", plugin.getFileName());
                entry.put("state", String.valueOf(plugin.getState()));
                Throwable error = plugin.getError();
                if (error != null) {
                    entry.put("error", error.getMessage());
                }
                plugins.add(entry);
            }
        } catch (Exception e) {
            report.put("pluginError", e.getMessage());
        }
        report.put("plugins", plugins);

        List<Map<String, Object>> devices = new ArrayList<>();
        for (NDevice device : getDevices()) {
            devices.add(describe(device));
        }
        report.put("devices", devices);
        report.put("devicesOfEveryType", scanEveryDeviceType());
        return report;
    }

    private static Map<String, Object> describe(NDevice device) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", device.getId());
        entry.put("displayName", device.getDisplayName());
        entry.put("make", device.getMake());
        entry.put("model", device.getModel());
        entry.put("types", String.valueOf(device.getDeviceType()));
        entry.put("isFingerScanner", device instanceof NFScanner);
        entry.put("available", device.isAvailable());
        return entry;
    }

    private void logDiscoveredDevices() {
        List<NFScanner> scanners = getFingerScanners();
        if (!scanners.isEmpty()) {
            LOG.info("Neurotec finger scanners: {}", describe(scanners));
            return;
        }
        LOG.error("Neurotec found no finger scanner. Check the 'Devices.FingerScanners' licence, "
                + "the device plugins under lamisplus.neurotec.plugin-search-path, and the vendor driver.");
        logPlugins();
        logEveryDeviceType();
    }

    private void logEveryDeviceType() {
        List<Map<String, Object>> all = scanEveryDeviceType();
        if (all.isEmpty()) {
            LOG.error("No device of ANY type is visible to the Neurotec SDK. Nothing is on the bus, or "
                    + "Windows has not bound a driver to it - check Device Manager with the scanner plugged in.");
            return;
        }
        LOG.warn("Devices visible once the finger-scanner filter is removed: {}", all.size());
        for (Map<String, Object> device : all) {
            LOG.warn("   {}", device);
        }
    }

    private void logPlugins() {
        try {
            for (NPlugin plugin : NDeviceManager.getPluginManager().getPlugins()) {
                Throwable error = plugin.getError();
                LOG.info("Device plugin {} -> {}{}", plugin.getFileName(), plugin.getState(),
                        error == null ? "" : " (" + error.getMessage() + ")");
            }
        } catch (Exception e) {
            LOG.warn("Could not list device plugins: {}", e.getMessage());
        }
    }

    private static List<ReaderMatcher.Candidate> describe(List<NFScanner> scanners) {
        List<ReaderMatcher.Candidate> candidates = new ArrayList<>();
        for (NFScanner scanner : scanners) {
            candidates.add(new ReaderMatcher.Candidate(
                    scanner.getDisplayName(), scanner.getMake(), scanner.getModel(), scanner.getId()));
        }
        return candidates;
    }

}
