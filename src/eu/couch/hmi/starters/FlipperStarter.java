package eu.couch.hmi.starters;

import hmi.flipper2.FlipperException;
import hmi.flipper2.launcher.FlipperLauncher;
import hmi.flipper2.launcher.FlipperLauncherThread;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import eu.couch.hmi.*;
import asap.bml.ext.bmlt.BMLTInfo;

public class FlipperStarter {

    private static FlipperLauncherThread flt;

    public static void main(String[] args) throws FlipperException, IOException {
        String mwPropFile = "defaultmiddleware.properties";
        String flipperPropFile = "couchflipper.properties";
        
        String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: \n\t-middlewareprops <filepath to file containing the default middleware properties (default: "+mwPropFile+")>\n\t-flipperprops <filepath to file containing the flipper properties  (default: "+flipperPropFile+")>\n";

        if(args.length > 0 && args.length % 2 != 0){
        	System.err.println("Invalid number of arguments: "+args.length);
          System.err.println(help);
          System.exit(0);
        }
        
        for(int i = 0; i < args.length; i = i + 2){
          if(args[i].equals("-middlewareprops")){
            mwPropFile = args[i+1];
          } else if(args[i].equals("-flipperprops")){
            flipperPropFile = args[i+1];
          } else {
              System.err.println("Unknown commandline argument: \""+args[i]+" "+args[i+1]+"\".\n"+help);
              System.exit(0);
          }
        }
      
        GenericMiddlewareLoader.setGlobalPropertiesFile(mwPropFile);

        BMLTInfo.init();

        Properties ps = new Properties();
        InputStream flipperPropStream = FlipperLauncher.class.getClassLoader().getResourceAsStream(flipperPropFile);

        try {
            ps.load(flipperPropStream);
        } catch (IOException ex) {
            System.out.println("Could not load flipper settings from "+flipperPropFile);
            ex.printStackTrace();
        }
        // If you want to check templates based on events (i.e. messages on middleware),
        // you can run  flt.forceCheck(); from a callback to force an immediate check.
        System.out.println("FlipperLauncher: Starting Thread");
        flt = new FlipperLauncherThread(ps);
        flt.start();

        //flt.stopGracefully();
    }
}

