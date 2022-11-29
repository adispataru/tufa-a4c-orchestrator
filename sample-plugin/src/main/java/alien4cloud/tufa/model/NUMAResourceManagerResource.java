package alien4cloud.tufa.model;

public class NUMAResourceManagerResource {
	private String resourceManager;
    private String endpoint;
    private String username;
    private String password;
    private String authKey;

    public NUMAResourceManagerResource() {
	this.resourceManager = ResourceType.MARATHON.name();
	this.endpoint = "";
	this.username = "";
	this.password = "";
	this.authKey = "";

    }

    /**
     * @return the resourceManager
     */
    public String getResourceManager() {
	return resourceManager;
    }

    /**
     * @param resourceManager
     *            the resourceManager to set
     */
    public void setResourceManager(String resourceManager) {
	this.resourceManager = resourceManager;
    }

    /**
     * @return the endpoint
     */
    public String getEndpoint() {
	return endpoint;
    }

    /**
     * @param endpoint
     *            the endpoint to set
     */
    public void setEndpoint(String endpoint) {
	this.endpoint = endpoint;
    }

    /**
     * @return the username
     */
    public String getUsername() {
	return username;
    }

    /**
     * @param username
     *            the username to set
     */
    public void setUsername(String username) {
	this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
	return password;
    }

    /**
     * @param password
     *            the password to set
     */
    public void setPassword(String password) {
	this.password = password;
    }

    /**
     * @return the authKey
     */
    public String getAuthKey() {
	return authKey;
    }

    /**
     * @param authKey
     *            the authKey to set
     */
    public void setAuthKey(String authKey) {
	this.authKey = authKey;
    }

}
