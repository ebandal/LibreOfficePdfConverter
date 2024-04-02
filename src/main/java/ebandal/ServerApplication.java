package ebandal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerApplication {
	
	public static void main(String[] args) throws Exception {
		ConfigurableApplicationContext ctx = SpringApplication.run(ServerApplication.class, args);
	}
	
}
