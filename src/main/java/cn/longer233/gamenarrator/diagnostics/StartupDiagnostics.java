package cn.longer233.gamenarrator.diagnostics;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupDiagnostics implements ApplicationRunner {

    private final SystemDiagnosticsService diagnostics;

    public StartupDiagnostics(SystemDiagnosticsService diagnostics) {
        this.diagnostics = diagnostics;
    }

    @Override
    public void run(ApplicationArguments args) {
        diagnostics.logStartupReport();
    }
}
