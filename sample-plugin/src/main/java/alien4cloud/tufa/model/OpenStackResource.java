package alien4cloud.tufa.model;

public class OpenStackResource {
    private String ipAddress;
    private String username;
    private String password;
    private String sshKey;

    public OpenStackResource() {
        this.ipAddress = "";
        this.username = "";
        this.password = "";
        this.sshKey = "";

    }

    /**
     * @return the ipAddress
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * @param ipAddress
     *            the ipAddress to set
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
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
     * @return the sshKey
     */
    public String getSshKey() {
        return sshKey;
    }

    /**
     * @param sshKey
     *            the sshKey to set
     */
    public void setSshKey(String sshKey) {
        this.sshKey = sshKey;
    }

}
