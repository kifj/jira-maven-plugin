package x1.maven.jira;

public class Deployment {
  private String groupId;
  private String artifactId;
  private String version;
  private String type;
  private String action;
  private String servers;
  private String slot;
  private String folder;
  private String name;
  private String artifactoryUrl;

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getServers() {
    return servers;
  }

  public void setServers(String servers) {
    this.servers = servers;
  }

  public String getSlot() {
    return slot;
  }

  public void setSlot(String slot) {
    this.slot = slot;
  }

  public String getFolder() {
    return folder;
  }

  public void setFolder(String folder) {
    this.folder = folder;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getArtifactoryUrl() {
    return artifactoryUrl;
  }

  public void setArtifactoryUrl(String artifactoryUrl) {
    this.artifactoryUrl = artifactoryUrl;
  }

  public String getGAV() {
    return groupId + ":" + artifactId + ":" + type + ":" + version;
  }

}
