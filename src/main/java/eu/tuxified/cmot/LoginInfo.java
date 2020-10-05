package eu.tuxified.cmot;

import java.io.*;

public class LoginInfo implements Serializable {
    public String getAasToken() {
        return aasToken;
    }

    public void setAasToken(String aasToken) {
        this.aasToken = aasToken;
    }

    public String getGsfId() {
        return gsfId;
    }

    public void setGsfId(String gsfId) {
        this.gsfId = gsfId;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getDeviceCheckinConsistencyToken() {
        return deviceCheckinConsistencyToken;
    }

    public void setDeviceCheckinConsistencyToken(String deviceCheckinConsistencyToken) {
        this.deviceCheckinConsistencyToken = deviceCheckinConsistencyToken;
    }

    public String getDeviceConfigToken() {
        return deviceConfigToken;
    }

    public void setDeviceConfigToken(String deviceConfigToken) {
        this.deviceConfigToken = deviceConfigToken;
    }

    public String getDfeCookie() {
        return dfeCookie;
    }

    public void setDfeCookie(String dfeCookie) {
        this.dfeCookie = dfeCookie;
    }

    private String aasToken;
    private String gsfId;
    private String authToken;
    private String deviceCheckinConsistencyToken;
    private String deviceConfigToken;
    private String dfeCookie;

    public LoginInfo() {

    }

    @Override
    public String toString() {
        return "LoginInfo{" +
                "aasToken='" + aasToken + '\'' +
                ", gsfId='" + gsfId + '\'' +
                ", authToken='" + authToken + '\'' +
                ", deviceCheckinConsistencyToken='" + deviceCheckinConsistencyToken + '\'' +
                ", deviceConfigToken='" + deviceConfigToken + '\'' +
                ", dfeCookie='" + dfeCookie + '\'' +
                '}';
    }

    public LoginInfo(String aasToken, String gsfId, String authToken, String deviceCheckinConsistencyToken, String deviceConfigToken, String dfeCookie) {
        this.aasToken = aasToken;
        this.gsfId = gsfId;
        this.authToken = authToken;
        this.deviceCheckinConsistencyToken = deviceCheckinConsistencyToken;
        this.deviceConfigToken = deviceConfigToken;
        this.dfeCookie = dfeCookie;
    }


    public void toFile(File path) throws IOException {
        FileOutputStream fout = new FileOutputStream(path);
        ObjectOutputStream oos = new ObjectOutputStream(fout);
        oos.writeObject(this);
        oos.close();
        fout.close();
    }

    public static LoginInfo fromFile(File path) throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(path);
        ObjectInputStream ois = new ObjectInputStream(fin);
        LoginInfo li = (LoginInfo) ois.readObject();
        ois.close();
        fin.close();
        return li;
    }
}
