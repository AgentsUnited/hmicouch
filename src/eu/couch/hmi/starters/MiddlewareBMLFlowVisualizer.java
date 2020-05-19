package eu.couch.hmi.starters;

import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.swing.JFrame;

import asap.middlewareadapters.BMLRealizerToMiddlewareAdapter;
import saiba.bmlflowvisualizer.BMLFlowVisualizerPort;

/*
 * TODO: pass middleware propertes
 * TODO: there are a lot of exceptions thrown to the bml parser. maybe related to bml extensions?
 */
public class MiddlewareBMLFlowVisualizer {
	

    protected JFrame mainJFrame = null;
    protected BMLRealizerToMiddlewareAdapter mtr;

	public static String ReadFile(String path) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream is = classloader.getResourceAsStream(path);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
    		return br.lines().collect(Collectors.joining(System.lineSeparator()));
    	} catch (IOException e) {
			return null;
		}
	}
	
    public static void main(String[] args) throws IOException {
		MiddlewareBMLFlowVisualizer bmlviswindow = new MiddlewareBMLFlowVisualizer();
		bmlviswindow.init(new JFrame("BML Flow Visualizer"));
    }

    public MiddlewareBMLFlowVisualizer() {}

    public void init(JFrame j) throws IOException {
    	mainJFrame = j;
    	String loaderClass = "nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader";
    	Properties loaderProperties = new Properties();
    	loaderProperties.put("iTopic", "COUCH/BML/FEEDBACK/ASAP");
    	loaderProperties.put("oTopic", "COUCH/BML/REQUEST/ASAP");
    	loaderProperties.put("amqBrokerURI", "tcp://localhost:61616");
    	mtr = new BMLRealizerToMiddlewareAdapter(loaderClass, loaderProperties);
    	
    	
        j.addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(WindowEvent winEvt)
            {
                System.exit(0);
            }
        });
        
        BMLFlowVisualizerPort visPort = new BMLFlowVisualizerPort(mtr);
        
        mainJFrame.setSize(1000, 1000);
        mainJFrame.add(visPort.getVisualization(), BorderLayout.CENTER);
        mainJFrame.setVisible(true);
        mainJFrame.revalidate();
        mainJFrame.repaint();
    }
    
}

