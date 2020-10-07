package eu.tuxified.cmot;

import com.dragons.aurora.playstoreapiv2.BulkDetailsEntry;
import com.dragons.aurora.playstoreapiv2.DocV2;
import com.dragons.aurora.playstoreapiv2.GooglePlayAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ApkStore {
    private Map<String, Apk> packages;
    private File artifactdir;

    private ApkStore(File artifactdir, Map<String, Apk> packages)  {
        this.artifactdir = artifactdir;
        this.packages = packages;
    }

    public List<Apk> getPackages() {
        return new ArrayList<>(packages.values());
    }

    // Credit goes to AuroraStore
    private void getRemoteAppList(GooglePlayAPI api) throws Exception {
        //final Map<String, Apk> packageNames = packages.stream().collect(Collectors.toMap(Apk::getId, Function.identity()));
        List<String> packageNames = new ArrayList<>(packages.keySet());
        final List<BulkDetailsEntry> bulkDetailsEntries = api.bulkDetails(packageNames).getEntryList();
        for (BulkDetailsEntry bulkDetailsEntry : bulkDetailsEntries) {
            if (!bulkDetailsEntry.hasDoc()) {
                continue;
            }

            DocV2 doc = bulkDetailsEntry.getDoc();
            Apk apk =  packages.get(doc.getDetails().getAppDetails().getPackageName());
            apk.registerDetails(doc);
        }
    }

    private static List<String> parseApkListFile(File file) throws IOException {
        return Files.lines(file.toPath()).map(String::strip).collect(Collectors.toList());
    }

    public static ApkStore fromConfig(Config config) throws IOException {
        HashMap<String, Apk> packages = new HashMap<>();
        for (String apkid : parseApkListFile(config.getApkListFile())) {
            var apk = Apk.fromConfig(apkid, config);
            packages.put(apkid, apk);
        }
        return new ApkStore(config.getArtifactDirectory(), packages);
    }

    public int runUpdate(GooglePlayAPI api) throws Exception {
        getRemoteAppList(api);
        int failures = 0;
        for (Apk apk : packages.values().stream().filter(Apk::updateAvailable).collect(Collectors.toList())) {
            try {
                System.out.print("Downloading " + apk.getId() + " : ");
                apk.download(api, artifactdir);
                apk.updateMetadata(api, artifactdir);
                System.out.println("success");
            } catch (Exception e) {
                failures++;
                System.out.println(" failure: " + e.getMessage());
            }
        }
        return failures;
    }
}
