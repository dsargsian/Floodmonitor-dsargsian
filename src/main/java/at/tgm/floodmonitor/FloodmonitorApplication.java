package at.tgm.floodmonitor;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FloodmonitorApplication {
    public static void main(String[] args) { SpringApplication.run(FloodmonitorApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
