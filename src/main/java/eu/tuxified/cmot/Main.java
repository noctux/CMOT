package eu.tuxified.cmot;

import com.aurora.store.adapter.OkHttpClientAdapter;
import com.dragons.aurora.playstoreapiv2.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.nio.file.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;

// Largely inspired by ApiBuilderUtil from AuroraStore

public class Main {
    public static DeviceInfoProvider getDeviceInfoProvider(Config config) throws IOException {
        var prop = new Properties();
        prop.load(ClassLoader.getSystemResourceAsStream("device-" + config.getDeviceDefinitionName() + ".properties"));
        PropertiesDeviceInfoProvider deviceInfoProvider = new PropertiesDeviceInfoProvider();
        deviceInfoProvider.setProperties(prop);
        deviceInfoProvider.setLocaleString(config.getLocale());
        return deviceInfoProvider;
    }

    private static void configureBuilderCommon(PlayStoreApiBuilder builder, Config config) throws IOException {
        builder.setHttpClient(new OkHttpClientAdapter());
        DeviceInfoProvider dip = getDeviceInfoProvider(config);
        builder.setDeviceInfoProvider(dip);
        builder.setLocale(new Locale(config.getLocale()));
    }

    private static GooglePlayAPI getApi(PlayStoreApiBuilder builder, Config config, LoginInfo loginInfo) throws IOException, ApiBuilderException {
        var api = builder.build();
        if (api != null) {
            System.out.println("Successfully retrieved api instance, storing token in cache");
            loginInfo.setGsfId(api.getGsfId());
            loginInfo.setAuthToken(api.getToken());
            loginInfo.setDfeCookie(api.getDfeCookie());
            loginInfo.setDeviceConfigToken(api.getDeviceConfigToken());
            loginInfo.setDeviceCheckinConsistencyToken(api.getDeviceCheckinConsistencyToken());
            System.out.println(loginInfo.toString());
            loginInfo.toFile(config.getCacheFile());
            System.out.println("Successfully serialized LoginInfo to " + config.getCacheFile());
        } else {
            throw new ApiBuilderException("builder.build() returned NULL");
        }
        return api;
    }

    private static GooglePlayAPI login(Config config) throws IOException, ApiBuilderException {
        System.out.println("Attempting new login using tokendispenser at " + config.getTokenDispenserUrl());
        PlayStoreApiBuilder builder = new PlayStoreApiBuilder();
        configureBuilderCommon(builder, config);
        builder.setTokenDispenserUrl(config.getTokenDispenserUrl());
        return getApi(builder, config, new LoginInfo());
    }

    private static GooglePlayAPI fromCachedToken(Config config) throws IOException, ApiBuilderException, ClassNotFoundException {
        System.out.println("Attempting to login using cached token");
        LoginInfo loginInfo = LoginInfo.fromFile(config.getCacheFile());
        PlayStoreApiBuilder builder = new PlayStoreApiBuilder();
        configureBuilderCommon(builder, config);
        builder.setAasToken(loginInfo.getAasToken());
        builder.setGsfId(loginInfo.getGsfId());
        builder.setAuthToken(loginInfo.getAuthToken());
        builder.setDeviceCheckinConsistencyToken(loginInfo.getDeviceCheckinConsistencyToken());
        builder.setDeviceConfigToken(loginInfo.getDeviceConfigToken());
        builder.setDfeCookie(loginInfo.getDfeCookie());
        return getApi(builder, config, loginInfo);
    }

    private static GooglePlayAPI getApi(Config config) throws IOException, ApiBuilderException {
        GooglePlayAPI api;
        try {
            api = fromCachedToken(config);
        } catch (Exception e) {
            System.out.println("Using cached token failed: " + e.getMessage());
            Path cache = config.getCacheFile().toPath();
            Files.deleteIfExists(cache);
            api = login(config);
        }

        return api;
    }

    private static String usage() {
        String exename = new java.io.File(Main.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();
        return exename + " <path/to/configfile>";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println(usage());
            System.exit(-1);
        }

        if (args[0] == "-h" || args[0] == "--help" || args[0] == "help") {
            System.out.println(usage());
            System.exit(0);
        }

        /*
        var list = new ArrayList<String>();
        list.add("f");
        list.add("g");
        var meta = new Apk.ApkMetadata(
                "a", "b", "c", "d", "e", list
        );
        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(new File("/tmp/foo.yml"), meta);
        System.exit(0);
        */

        Config config = Config.fromConfigFile(args[0]);
        System.err.println(config.getApkListFile().toString());
        ApkStore store = ApkStore.fromConfig(config);
        GooglePlayAPI api = getApi(config);
        System.exit(store.runUpdate(api));
    }
}
