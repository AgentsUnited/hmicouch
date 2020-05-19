package eu.couch.hmi.environments;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.middleware.IDMiddlewareListener;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

/**
 * Environment for managing the authentication with the Wool-Web-Services (WWS) authentication component hosted by RRD
 * This returns an authentication token that any subsequent requests must place in their "X-Auth-Token" header
 * @author Daniel
 *
 */
public class AuthEnvironment extends MiddlewareEnvironment {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(AuthEnvironment.class.getName());
	private String username;
	private String password;

	private SSOFrame ssoFrame;
	
	private String[] requiredMiddlewares = new String[] {"AuthServiceMiddleware","SSOMiddleware", "SSOCCEMiddleware"};
	
	private ObjectMapper om;
	private boolean isAuthenticated = false;
	private String authToken;

	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		for(IFlipperEnvironment e : envs) {
			logger.warn("AuthEnvironment doesn't need environment: {}", e.getId());
		}
	}

	@Override
	public void init(JsonNode params) throws Exception {
		om = new ObjectMapper();		
		loadRequiredMiddlewares(params, requiredMiddlewares);
		//TODO: update SSO HTTP endpoint in Environments.xml when tessa supplies the endpoint

		
		//TODO: in the future, these may come at runtime from a user input, which means we should wait with authenticating...
		// but waiting too long will also cause delay of initialisation of the other environments... something to think about
		// maybe use a callback onAuthenticated() or something
        if (params.has("username") && params.has("password")) {
        	username = params.get("username").asText();
        	password = params.get("password").asText();
        	
    		ssoFrame = new SSOFrame("COUCH SSO", username, password, new SSOLoginActionListener(), new SSOLogoutActionListener());
        } else {
        	throw new Exception("AuthEnvironment requires params username and password for authentication");
        }
	}

	@Override
	public void receiveDataFromMW(String mw, JsonNode jn) {
		if(mw.equals("AuthServiceMiddleware")) {
			logger.info("Got authentication response: {}", jn.toString());
			//TODO: check if we actually got a valid token
			authToken = jn.findPath("token").asText();
			isAuthenticated = true;
		}
	}
	
	/**
	 * Does a blocking POST request to the WWS server with the supplied username and password to retrieve an authentication token
	 */
	public void authenticate() {
		username = ssoFrame.getUsername();
		password = ssoFrame.getPassword();
		logger.debug("User entered username {} and password {}", username, password);
		
		logger.info("Authenticating with WWS");
		getMW("AuthServiceMiddleware").sendData(om.valueToTree(new AuthRequest(username, password)));

        //wait for it to be authenticated :)
        long startTime = System.currentTimeMillis();
        while(!isAuthenticated) {
        	if(System.currentTimeMillis() - startTime > 5000) {
        		logger.error("Timeout while authenticating - Unable to authenticate with WWS");
        		throw new RuntimeException("Timeout while authenticating - Unable to authenticate with WWS");
        	}
        	try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

	}
	
	/**
	 * Sends the authtoken & username to other modules listening on the SSO middleware (and the CCE HTTP endpoint)
	 */
	private void doSSOLogin() {
		JsonNode jn = om.valueToTree(new SSOLogin(username, authToken));
		logger.info("Sending SSO login for user: {}", username);
		getMW("SSOMiddleware").sendData(jn);
		getMW("SSOCCEMiddleware").sendData(jn);
	}
	
	private void doSSOLogout() {
		JsonNode jn = om.valueToTree(new SSOLogout(username));
		logger.info("Sending SSO logout for user: {}", username);
		getMW("SSOMiddleware").sendData(jn);
		getMW("SSOCCEMiddleware").sendData(jn);
		this.authToken = "";
		this.username = "";
		//TODO: also make all other environments forget the authToken
	}
	
	public boolean isAuthenticated() {
		return isAuthenticated ;
	}

	/**
	 * Blocking call to wait for the authentication to complete, this may take a while before the user presses the login button
	 * Set a negative timeoutMillis to wait indefinitely
	 * @param timeout a maximum timeout (in ms) to wait for authentication, a negative value is infinite waiting time
	 * @return true iff successfully authenticated
	 */
	public boolean waitForAuthentication(long timeout) {
		long startTime = System.currentTimeMillis();
		while(!isAuthenticated) {
	    	if(timeout > 0 && System.currentTimeMillis() - startTime > timeout) {
	    		logger.info("Timeout while waiting for authentication");
	    		return false;
	    	}
        	try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
		return true;
	}
	
	public String getAuthToken() {
		return authToken;
	}
	
	public String getUsername() {
		return username;
	}

	private class SSOLoginActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			ssoFrame.disableLogin();
			authenticate();
			doSSOLogin();
		}
	}

	private class SSOLogoutActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			doSSOLogout();
		}
	}
	
}

/**
 * A small GUI window for entering the username and password for SSO
 * @author Daniel
 *
 */
class SSOFrame extends JFrame
{
	JLabel lDescription;
	JLabel lUsername;
	JLabel lPassword;
	JTextField tfUsername;
	JTextField tfPassword;
	JButton bLogin; 
	JButton bLogout; 
	
	SSOFrame(String title, String defaultUsername, String defaultPassword, ActionListener loginAL, ActionListener logoutAL) {
		super(title);
		setLayout(new FlowLayout());

		lDescription = new JLabel("SSO for Flipper, CCE and DAF");
		add(lDescription);

		lUsername = new JLabel("Username:");
		add(lUsername);
		
		tfUsername = new JTextField(defaultUsername, 20);
		add(tfUsername);

		lUsername = new JLabel("Password:");
		add(lUsername);
		
		tfPassword = new JTextField(defaultPassword, 20);
		add(tfPassword);
		
		bLogin = new JButton("Login");
		bLogin.addActionListener(loginAL);
		add(bLogin);
		
		bLogout = new JButton("Logout");
		bLogout.addActionListener(logoutAL);
		add(bLogout);
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);   
		
		this.setSize(250, 200);     
		this.setVisible(true); 
	}

	public String getUsername() {
		return tfUsername.getText();
	}

	public String getPassword() {
		return tfPassword.getText();
	}
	
	public void disableLogin() {
		tfUsername.setEnabled(false);
		tfPassword.setEnabled(false);
		bLogin.setEnabled(false);
	}
}


/////////////////////////////////
// JSON protocol (de)serialisation classes below

//for authenticating with r2d2 or wws
class AuthRequest{
	public String user;
	public String password;
	
	public AuthRequest(String user, String password) {
		this.user = user;
		this.password = password;
	}
}

// for the single sign on
//{"cmd":"login","username":"test@test.com","authToken":"xyz"} and {"cmd":"logout","username":"test@test.com"}
class SSOLogin {
	public String cmd;
	public String username;
	public String authToken;
	
	public SSOLogin() {cmd = "login";}
	public SSOLogin(String username, String authToken) {
		this();
		this.username = username;
		this.authToken = authToken;
	}
}
class SSOLogout {
	public String cmd;
	public String username;
	
	public SSOLogout() {cmd = "logout";}
	public SSOLogout(String username) {
		this();
		this.username = username;
	}
}
