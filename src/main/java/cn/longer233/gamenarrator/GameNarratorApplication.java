package cn.longer233.gamenarrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class GameNarratorApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GameNarratorApplication.class);
        application.addInitializers(new ProductionCredentialGuard(), new LocalOnlyServerBindingGuard());
        application.run(args);
    }
}
