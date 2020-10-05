package eu.tuxified.cmot;

import java.io.*;
import java.lang.module.Configuration;
import java.util.Properties;

public class Config {
    private File apkListFile;
    private File artifactDirectory;
    private File cacheFile;
    private String tokenDispenserUrl;
    private String deviceDefinitionName;
    private String locale;

    public File getApkListFile() {
        return apkListFile;
    }

    public File getArtifactDirectory() {
        return artifactDirectory;
    }

    public File getCacheFile() {
        return cacheFile;
    }

    public String getTokenDispenserUrl() {
        return tokenDispenserUrl;
    }

    public String getDeviceDefinitionName() {
        return deviceDefinitionName;
    }

    public String getLocale() {
        return locale;
    }

    public Config(File apkListFile, File artifactDirectory, File cacheFile, String tokenDispenserUrl, String deviceDefinitionName, String locale) {
        this.apkListFile = apkListFile;
        this.artifactDirectory = artifactDirectory;
        this.cacheFile = cacheFile;
        this.tokenDispenserUrl = tokenDispenserUrl;
        this.deviceDefinitionName = deviceDefinitionName;
        this.locale = locale;
    }

    private static File resolvePotentialleRelativeToConfig(String path, File Config) {
        if (path.startsWith(".")) {
            return new File(Config.getParentFile(), path);
        } else {
            return new File(path);
        }
    }

    public static Config fromConfigFile(String propertiesFilePath) throws IOException, ConfigurationFormatException {
        Properties p = new Properties();
        BufferedInputStream stream = new BufferedInputStream(new FileInputStream(propertiesFilePath));
        p.load(stream);
        stream.close();

        File config = new File(propertiesFilePath);

        return new Config(
                resolvePotentialleRelativeToConfig(getKeyNonNull(p, "apkListFile"), config),
                resolvePotentialleRelativeToConfig(getKeyNonNull(p, "artifactDirectory"), config),
                resolvePotentialleRelativeToConfig(getKeyNonNull(p, "cacheFile"), config),
                getKeyNonNull(p, "tokenDispenserUrl"),
                p.getProperty("deviceDefinitionName", "bacon"),
                p.getProperty("locale", "us")
        );
    }

    public static String getKeyNonNull(Properties p, String key) throws ConfigurationFormatException {
        String res = p.getProperty(key);
        if (res == null) {
            throw new ConfigurationFormatException("Required key '" + key + "' is missing in configuration file");
        }
        return res;
    }
}
