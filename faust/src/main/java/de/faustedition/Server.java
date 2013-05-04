package de.faustedition;

import com.google.common.collect.Iterables;
import org.restlet.Component;
import org.restlet.data.Protocol;
import org.restlet.util.ClientList;
import org.slf4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@org.springframework.stereotype.Component
public class Server extends Runtime implements Runnable, InitializingBean {
	@Autowired
	private Environment environment;

	@Autowired
	private FaustApplication application;

	@Autowired
	private Logger logger;

	private String contextPath;

	@Override
	public void afterPropertiesSet() throws Exception {
		this.contextPath = environment.getRequiredProperty("ctx.path");
	}

	public static void main(String[] args) throws Exception {
		main(Server.class, args);
	}

	@Override
	public void run() {
		try {
			logger.info("Starting Faust-Edition with profiles " + Iterables.toString(Arrays.asList(environment.getActiveProfiles())));

			startWebserver();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void startWebserver() throws Exception {
		final Component component = new Component();
		component.getServers().add(Protocol.HTTP, environment.getRequiredProperty("server.port", Integer.class));

		ClientList clients = component.getClients();
		clients.add(Protocol.FILE);
		clients.add(Protocol.HTTP).setConnectTimeout(4000);
		clients.add(Protocol.HTTPS).setConnectTimeout(4000);

		logger.info("Mounting application under '" + contextPath + "/'");
		component.getDefaultHost().attach(contextPath + "/", application);
		component.getLogService().setEnabled(false);
		component.start();
	}
}
