package main;

import java.io.IOException;

import com.safran.checker.plugin.SafranCheckerPlugin;
import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

public class RhapsodyLiveDebug {

	public static void main(String[] args)
	{
		IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplication();

		SafranCheckerPlugin safran = new SafranCheckerPlugin();
		safran.RhpPluginInit(app);

		System.out.println("connected");

		// to make sure that the client keeps listening Rhapsody,
		// without terminating, use a blocking I/O operation.
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}
}
