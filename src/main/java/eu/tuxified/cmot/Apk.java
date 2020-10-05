package eu.tuxified.cmot;

import com.dragons.aurora.playstoreapiv2.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Apk {
    private AndroidAppDeliveryData deliveryData;
    private String downloadToken;

    private static class StoredInstance {
        File path;
        long versionCode;

        public StoredInstance(File path, long versionCode) {
            this.path = path;
            this.versionCode = versionCode;
        }
    }

    private String id;
    private ArrayList<StoredInstance> instances;
    private DocV2 doc;

    private Apk(String id) {
        this.id = id;
        this.instances = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    private static File getApkDirectory(File artifactdir) {
        return new File(artifactdir + File.separator + "repo");
    }

    private static File getMetadataDirectory(File artifactdir) {
        return new File(artifactdir + File.separator + "metadata");
    }

    private void collectApks(File artifactdirectory) {
        var list = new ArrayList<StoredInstance>();
        Pattern pat = Pattern.compile("^" + Pattern.quote(this.id) + "-(\\d+)\\.apk$");
        for(File f : Objects.requireNonNull(getApkDirectory(artifactdirectory).listFiles())) {
            Matcher m = pat.matcher(f.getName());
            if (f.isFile() && m.find()) {
                list.add(new StoredInstance(f.getAbsoluteFile(), Long.parseUnsignedLong(m.group(1))));
            }
        }
        instances = list;
    }

    public static Apk fromConfig(String pkgid, Config config) {
        var apk = new Apk(pkgid);
        apk.collectApks(config.getArtifactDirectory());
        return apk;
    }

    public void registerDetails(DocV2 doc) {
        this.doc = doc;
    }

    public boolean updateAvailable() {
        return doc == null ||
                instances.isEmpty() ||
                instances.stream().noneMatch(instance -> instance.versionCode == doc.getDetails().getAppDetails().getVersionCode());
    }

    public void download(GooglePlayAPI api, File directory) throws Exception {
        if (doc == null) {
            throw new ApiUsageException("You have to check for available updates first using updateAvailable()");
        }

        purchase(api);
        delivery(api, directory);
    }

    private int getRemoteVersionCode() {
        return doc.getDetails().getAppDetails().getVersionCode();
    }

    private int getRemoteOfferType() {
        return (doc.getOfferCount() > 0 ? doc.getOffer(0).getOfferType() : 0);
    }

    // From AuroraStore
    private void purchase(GooglePlayAPI api) throws IOException {
        BuyResponse buyResponse = api.purchase(id, getRemoteVersionCode(), getRemoteOfferType());
        if (buyResponse.hasPurchaseStatusResponse()
                && buyResponse.getPurchaseStatusResponse().hasAppDeliveryData()
                && buyResponse.getPurchaseStatusResponse().getAppDeliveryData().hasDownloadUrl()) {
            deliveryData = buyResponse.getPurchaseStatusResponse().getAppDeliveryData();
        }
        if (buyResponse.hasDownloadToken()) {
            downloadToken = buyResponse.getDownloadToken();
        }
    }

    // From AuroraStore
    private void delivery(GooglePlayAPI api, File dir) throws Exception {
        DeliveryResponse deliveryResponse = api.delivery(
                id,
                0, // Never download deltas
                getRemoteVersionCode(),
                getRemoteOfferType(),
                downloadToken);
        if (deliveryResponse.hasAppDeliveryData()
                && deliveryResponse.getAppDeliveryData().hasDownloadUrl()) {
            deliveryData = deliveryResponse.getAppDeliveryData();
        } else if (deliveryData == null && deliveryResponse.hasStatus()) {
            handleError(this, deliveryResponse.getStatus());
        }
        doDownload(new URL(deliveryData.getDownloadUrl()),
                new File(getApkDirectory(dir) + File.separator + id + "-" + getRemoteVersionCode() + ".apk"));
    }

    private void doDownload(URL url, File destination) throws IOException {
        try {
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            FileOutputStream fos = new FileOutputStream(destination);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            // Ensure that no incomplete Apks leak...
            Files.deleteIfExists(destination.toPath());
        }
    }

    public static class ApkMetadata {
        public final String[] AntiFeatures = {"Ads", "Tracking", "UpstreamNonFree", "NonFreeNet"};
        public final String License = "Unknown";
        public final String AutoUpdateMode = "None";
        public final String UpdateCheckMode = "None";
        public final String MaintainerNotes = "Automatically exported from GooglePlay... No promises!";
        public final String WebSite;
        public final String Summary;
        public final String Description;
        public final String AuthorName;
        public final String AuthorEmail;
        public final List<String> Categories;

        public ApkMetadata(String webSite, String summary, String description, String authorName, String authorEmail, List<String> categories) {
            WebSite = webSite;
            Summary = summary;
            Description = formatDescription(description);
            AuthorName = authorName;
            AuthorEmail = authorEmail;
            Categories = categories;
        }

        private static String replaceEntities(String s) {
            // Entity list
            final HashMap<String, String> entities = new HashMap<String,String>();
            entities.put("&lt;", "<");
            entities.put("&gt;", ">");
            entities.put("&amp;", "&");
            entities.put("&#39;", "'");

            for (Map.Entry<String, String> entity : entities.entrySet()) {
                s = s.replaceAll(entity.getKey(), entity.getValue());
            }
            return s;
        }

        private static String markUrls(String s) {
            s = s.replaceAll("(\\S)(http(s?)://)", "$1 $2");
            return s.replaceAll("(http(s?)://(\\.[^\\s\\.]|[^\\s\\.])+)(\\.\\s|\\s|$|\\.$)", "[$1]$4");
        }

        private static String breakLongLine(String line, int breakoff) {
            String[] tokens = line.split("\\s+");

            int linelength = 0;
            StringBuilder sb = new StringBuilder();

            for (String token : tokens) {
                if (linelength + 1 + token.length() >= breakoff) {
                    sb.append("\n");
                    linelength = 0;
                } else if (sb.length() > 0) {
                    sb.append(" ");
                    linelength += 1;
                }

                sb.append(token);
                linelength += token.length();
            }

            return sb.toString();
        }

        private static String formatDescription(String desc) {
            StringBuilder sb = new StringBuilder();

            String[] lines = desc.split("<br>|<br/>|<p>");

            for (String line : lines) {
                // Linebreaks are handled by two newlines
                if (sb.length() != 0) {
                    sb.append("\n");
                }

                // Trailing/leading whitespace
                line = line.trim();

                line = replaceEntities(line);
                line = markUrls(line);
                line = breakLongLine(line, 80);

                sb.append(line);
                sb.append("\n");
            }

            sb.append(".\n");

            return sb.toString();
        }

    }

    public void updateMetadata(GooglePlayAPI api, File artifactdir) throws IOException {
        doc = api.details(id).getDocV2();
        AppDetails details = doc.getDetails().getAppDetails();
        ApkMetadata meta = new ApkMetadata(
                details.getDeveloperWebsite(),
                details.getTitle(),
                doc.getDescriptionHtml(),
                details.getDeveloperName(),
                details.getDeveloperEmail(),
                details.getAppCategoryList()
        );
        File metadatadir = getMetadataDirectory(artifactdir);
        var tmpfile = File.createTempFile(id, ".tmp", metadatadir);

        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(tmpfile, meta);

        tmpfile.renameTo(new File(metadatadir + File.separator + id + ".yml"));
    }

    // From AuroraStore
    private void handleError(Apk app, int statusCode) throws Exception {
        switch (statusCode) {
            case 2:
                throw new AppNotFoundException("App " + app.getId() + ": " + statusCode);
            case 3:
                throw new NotPurchasedException("App " + app.getId() + ": " + statusCode);
            default:
                throw new Exception("Unknown error while delivering apk " + app.getId() + ", statuscode: " + statusCode);
        }
    }

}
