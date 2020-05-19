package eu.couch.hmi.starters;

import java.util.Properties;
import asap.middlewareadapters.*;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;
import asap.realizerport.*;
import asap.bml.bridge.ui.FeedbackPanel;
import asap.bml.bridge.ui.RealizerPortUI;
import asap.realizerembodiments.*;
import asap.activemqadapters.*;
import java.io.IOException;
import java.util.ArrayList;
import asap.bml.ext.bmlt.BMLTInfo;
import asap.environment.AsapEnvironment;
import asap.realizerembodiments.SharedPortLoader;
import hmi.audioenvironment.AudioEnvironment;
import hmi.environmentbase.ClockDrivenCopyEnvironment;
import hmi.environmentbase.Environment;
import hmi.mixedanimationenvironment.MixedAnimationEnvironment;
import hmi.physicsenvironment.OdePhysicsEnvironment;
import hmi.unityembodiments.loader.SharedMiddlewareLoader;
import hmi.worldobjectenvironment.WorldObjectEnvironment;
import saiba.bml.BMLInfo;
import saiba.bml.core.FaceLexemeBehaviour;
import saiba.bml.core.HeadBehaviour;
import saiba.bml.core.PostureShiftBehaviour;
import java.awt.Toolkit;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import asap.middlewareengine.bml.*;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BmlWindowStarter {
    private Logger logger = LoggerFactory.getLogger(JFrameEmbodiment.class.getName());
    private JFrame theUI = null;
    private JPanel contentPanel;
    private static Properties ps;
    
    static
	{
		BMLInfo.addBehaviourType(SendMessageBehavior.xmlTag(),SendMessageBehavior.class);
	}
	
    private static String windowTitle = "BML input window";
    private static String bmlRequestTopic = "asap.bml.request";
    private static String bmlFeedbackTopic = "asap.bml.feedback";
    private static String activeMQHost = null;
    private static String scriptDir = "bmlexamples";
    
    public static void main(String[] args) throws IOException {
    	String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: \n\t title <titleOfWindow> \n\t requesttopic <ActiveMQ.topic.for.bml.requests> \n\t feedbacktopic <ActiveMQ.topic.for.bml.feedback> \n\t activemquri <host uri, e.g. tcp://localhost:61616> \n\t scriptdir <directory with bml example scripts relative to resources> \n\t middlewareprops <filepath to file containing the default middleware properties> (overridden by more specific settings above!)";

        String mwPropFile = "defaultmiddleware.properties";
    	
        if(args.length % 2 != 0){
        	System.err.println(help);
        	System.exit(1);
        }
        
        for(int i = 0; i < args.length; i = i + 2){
        	if(args[i].equals("-title")){
        		windowTitle = args[i+1];
        	} else if(args[i].equals("-middlewareprops")){
        		mwPropFile = args[i+1];
        	} else if(args[i].equals("-requesttopic")){
        		bmlRequestTopic = args[i+1];
        	} else if(args[i].equals("-feedbacktopic")){
        		bmlFeedbackTopic = args[i+1];
        	} else if(args[i].equals("-activemquri")){
        		activeMQHost = args[i+1];
        	} else if(args[i].equals("-scriptdir")){
        		scriptDir = args[i+1];
        	} else {
            	System.err.println("Unknown commandline argument: \""+args[i]+" "+args[i+1]+"\".\n"+help);
            	System.exit(1);
        	}
        }
        GenericMiddlewareLoader.setGlobalPropertiesFile(mwPropFile);
        ps = GenericMiddlewareLoader.getGlobalProperties();
        if (ps==null)ps=new Properties();
        ps.setProperty("iTopic",bmlFeedbackTopic);
        ps.setProperty("oTopic",bmlRequestTopic);
        if (activeMQHost!=null)ps.setProperty("amqBrokerURI",activeMQHost);
    	BmlWindowStarter bws = new BmlWindowStarter();
    	bws.init();
    }

    public BmlWindowStarter() {
    }

    public void addJComponent(JComponent jc)
    {
        jc.setAlignmentX(JFrame.LEFT_ALIGNMENT);
        contentPanel.add(jc);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    public void init() throws IOException {
        RealizerPort rp = new  BMLRealizerToMiddlewareAdapter("nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader", ps);

        try
        {
            SwingUtilities.invokeAndWait(new Runnable()
            {

                @Override
                public void run()
                {
                    // make UI frame
                    theUI = new JFrame(windowTitle);
                    theUI.setLocation(650, 50);
                    theUI.setSize(800, 600);
                    theUI.setVisible(true);
                    contentPanel = new JPanel();
                    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
                    contentPanel.setAlignmentX(JFrame.LEFT_ALIGNMENT);
                    contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                    theUI.getContentPane().add(contentPanel);
                    addJComponent(new RealizerPortUI(rp, scriptDir));
                    addJComponent(new FeedbackPanel(rp));
                }
            });
        }
        catch (InterruptedException e)
        {
            logger.warn("Exception in JFrameEmbodiment initialization", e);
            Thread.interrupted();
        }
        catch (InvocationTargetException e)
        {
            logger.warn("Exception in JFrameEmbodiment initialization", e);
        }
        

    }
}

