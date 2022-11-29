package alien4cloud.tufa.model;

public class OpenStackAccountResource {
    private String platform;
    private String domain;
    private String project;
    private String username;
    private String password;
    private String authEndpoint;

    public OpenStackAccountResource() {
	this.platform = ResourceType.OPENSTACK_ACCOUNT.name();
	this.domain = "";
	this.project = "";
	this.username = "";
	this.password = "";
	this.authEndpoint = "";
    }

    /**
     * @return the platform
     */
    public String getPlatform() {
	return platform;
    }

    /**
     * @param platform
     *            the platform to set
     */
    public void setPlatform(String platform) {
	this.platform = platform;
    }

    /**
     * @return the domain
     */
    public String getDomain() {
	return domain;
    }

    /**
     * @param domain
     *            the domain to set
     */
    public void setDomain(String domain) {
	this.domain = domain;
    }

    /**
     * @return the project
     */
    public String getProject() {
	return project;
    }

    /**
     * @param project
     *            the project to set
     */
    public void setProject(String project) {
	this.project = project;
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
     * @return the authEndpoint
     */
    public String getAuthEndpoint() {
	return authEndpoint;
    }

    /**
     * @param authEndpoint
     *            the authEndpoint to set
     */
    public void setAuthEndpoint(String authEndpoint) {
	this.authEndpoint = authEndpoint;
    }

}
