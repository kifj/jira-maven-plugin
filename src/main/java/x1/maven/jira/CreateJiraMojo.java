package x1.maven.jira;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * Create JIRA tickets for deployment with different templates according to the
 * stage
 * 
 * @author joe
 */
@Mojo(name = "create")
public class CreateJiraMojo extends AbstractMojo {
  /** URL for JIRA instance */
  @Parameter(property = "jira.server")
  private URL jiraServer;

  /** username for JIRA */
  @Parameter(property = "jira.username")
  private String jiraUserName;

  /** password for JIRA */
  @Parameter(property = "jira.password")
  private String jiraPassword;

  /** project name in JIRA */
  @Parameter(property = "jira.project")
  private String project;

  /** override template file (JSON) for create the JIRA issue */
  @Parameter(property = "jira.template", defaultValue = "create-jira.vm")
  private String templateFile;

  /** approver of the issue */
  @Parameter(property = "jira.approver", defaultValue = "")
  private String approver;

  /** assignee of the issue */
  @Parameter(property = "jira.assignee", defaultValue = "")
  private String assignee;

  /** reporter of the issue */
  @Parameter(property = "jira.reporter", defaultValue = "")
  private String reporter;

  /** list of watchers for issue, separated by comma */
  @Parameter(property = "jira.watchers", defaultValue = "")
  private String[] watchers;

  /** list of labels for issue, separated by comma */
  @Parameter(property = "jira.labels")
  private String[] labels;

  /** list of links for issue, separated by comma */
  @Parameter(property = "jira.links")
  private String[] links;

  /** due date ofr the issue (yyyy-MM-dd) */
  @Parameter(property = "jira.duedate")
  private String dueDate;

  /** component for the issue */
  @Parameter(property = "jira.component")
  private String component;

  /** name of the repository for releases in Maven settings */
  @Parameter(property = "artifactory.releases")
  private String repository;

  /** name of the repository for snapshots in Maven settings */
  @Parameter(property = "artifactory.snapshots")
  private String snapshotRepository;

  /** groupId of deployment artifact */
  @Parameter(property = "deployment.groupId")
  private String groupId;

  /** artifactId of deployment artifact */
  @Parameter(property = "deployment.artifactId")
  private String artifactId;

  /** version of deployment artifact */
  @Parameter(property = "deployment.version")
  private String version;

  /** type of deployment artifact */
  @Parameter(property = "deployment.type", defaultValue = "war")
  private String type;

  /** action for deployment (deploy, undeploy) */
  @Parameter(property = "deployment.action", defaultValue = "deploy")
  private String action;

  /** list of host names, separated by comma */
  @Parameter(property = "deployment.servers")
  private String servers;

  /** name of the stage in which the deployment takes place (prod, test) */
  @Parameter(property = "deployment.stage")
  private String stage;

  /** number of the slot / cluster */
  @Parameter(property = "deployment.slot", defaultValue = "0")
  private String slot;

  /** name of the folder for the deployment */
  @Parameter(property = "deployment.folder", defaultValue = "")
  private String folder;

  /** name of the artifact on the target system */
  @Parameter(property = "deployment.name")
  private String name;

  /** URL of the artifact in Artifactory */
  @Parameter(property = "deployment.artifactoryUrl")
  private String artifactoryUrl;

  /** a list of deployments for configuration in a pom */
  @Parameter(property = "deployments")
  private List<Deployment> deployments;

  @Parameter(defaultValue = "${settings}", readonly = true)
  private Settings settings;

  public void execute() throws MojoExecutionException {
    try {
      loadDefaults(stage);
      String body = fillTemplate(stage, templateFile, getTemplateVariables());
      getLog().debug("Create JIRA-Ticket in " + jiraServer + ":\n" + body);
      CreateResult createResult = new JiraClient(jiraServer, jiraUserName, jiraPassword, getLog()).createIssue(body);
      String issueKey = createResult.getKey();
      getLog().info("Created issue " + jiraServer + "/issue/" + issueKey);
      for (String watcher : watchers) {
        body = fillTemplate(null, "add-watcher.vm", getWatcherVariables(issueKey, watcher));
        getLog().debug("Add watcher: " + watcher + "\n" + body);
        new JiraClient(jiraServer, jiraUserName, jiraPassword, getLog()).addWatcher(issueKey, body);
      }
      for (String link : links) {
        body = fillTemplate(null, "link-issue.vm", getLinkVariables(issueKey, link));
        getLog().debug("Add link: " + link + "\n" + body);
        new JiraClient(jiraServer, jiraUserName, jiraPassword, getLog()).addLink(body);
      }
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage());
    }
  }

  private String getTitle() {
    return "[" + stage.toUpperCase() + "] Deployment " + name + " (" + version + ")";
  }

  private Map<String, Object> getTemplateVariables() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("title", getTitle());
    variables.put("project", project);
    variables.put("stage", stage);
    variables.put("labels", labels);

    if (deployments.isEmpty()) {
      Deployment deployment = new Deployment();
      deployment.setAction(action);
      deployment.setArtifactId(artifactId);
      deployment.setFolder(folder);
      deployment.setGroupId(groupId);
      deployment.setName(name);
      deployment.setServers(servers);
      deployment.setSlot(slot);
      deployment.setVersion(version);
      if (artifactoryUrl != null) {
        deployment.setArtifactoryUrl(artifactoryUrl);
      } else {
        deployment.setArtifactoryUrl(getArtifactoryUrl(groupId, artifactId, version, type));
      }
      variables.put("deployments", Arrays.asList(deployment));
    } else {
      for (Deployment deployment : deployments) {
        if (deployment.getArtifactoryUrl() == null) {
          deployment.setArtifactoryUrl(getArtifactoryUrl(deployment.getGroupId(), deployment.getArtifactId(),
              deployment.getVersion(), deployment.getType()));
        }
        if (deployment.getAction() == null) {
          deployment.setAction(action);
        }
      }
      variables.put("deployments", deployments);
    }

    variables.put("approver", approver);
    variables.put("assignee", assignee);
    variables.put("reporter", reporter);
    variables.put("duedate", dueDate);
    variables.put("component", component);
    return variables;
  }

  private Map<String, Object> getWatcherVariables(String id, String watcher) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("id", id);
    variables.put("watcher", watcher);
    return variables;
  }

  private Map<String, Object> getLinkVariables(String id, String link) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("id", id);
    variables.put("link", link);
    return variables;
  }

  private String fillTemplate(String stage, String template, Map<String, Object> variables) throws IOException {
    VelocityEngine ve = new VelocityEngine();

    File f = new File(template);
    getLog().debug("Checking file " + f + " -> " + f.exists());
    String resource;
    if (f.exists()) {
      ve.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, f.getParentFile().getAbsolutePath());
      resource = f.getName();
    } else {
      ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
      ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
      resource = (stage != null) ? stage + "/" + template : template;
    }
    ve.init();
    Template t = ve.getTemplate(resource);
    VelocityContext context = new VelocityContext();
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      context.put(entry.getKey(), entry.getValue());
    }
    StringWriter writer = new StringWriter();
    t.merge(context, writer);
    return writer.getBuffer().toString();
  }

  private String getArtifactoryUrl(String groupId, String artifactId, String version, String type) {
    String artifactoryServer = "http://localhost";
    // TODO fix artifactory URL resolution for SNAPSHOTs
    // when version contains SNAPSHOT the server will return the latest SNAPSHOT
    // (Artifactory) or 404 (Nexus)
    // when version is a dedicated SNAPSHOT (as in
    // stomp-test-1.4.0-20161002.090310-15.war) we would query the
    // release profile.
    boolean isSnapshot = version.contains("-SNAPSHOT");
    String id = (isSnapshot ? snapshotRepository : repository);

    getLog().debug("Looking for repository " + id + " in profiles " + settings.getActiveProfiles());

    for (String profile : settings.getActiveProfiles()) {
      Profile p = settings.getProfilesAsMap().get(profile);
      if (p != null) {
        for (Repository r : p.getRepositories()) {
          if (r.getId().equals(id)) {
            artifactoryServer = r.getUrl();
          }
        }
      }
    }

    return artifactoryServer + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId
        + "-" + version + "." + type;
  }

  private void loadDefaults(String stage) throws IOException {
    Properties p = new Properties();
    InputStream is = getClass().getClassLoader().getResourceAsStream(stage + "/jira.properties");
    if (is != null) {
      p.load(is);
      is.close();

      if (jiraServer == null && p.getProperty("jira.server") != null) {
        jiraServer = new URL(p.getProperty("jira.server"));
      }
      if (project == null && p.getProperty("jira.project") != null) {
        project = p.getProperty("jira.project");
      }
      if (repository == null) {
        repository = p.getProperty("artifactory.releases", "releases");
      }
      if (snapshotRepository == null) {
        snapshotRepository = p.getProperty("artifactory.snapshots", "snapshots");
      }
      if (p.getProperty("settings.server") != null) {
        loadJiraServer(p.getProperty("settings.server"));
      }
    }
  }

  private void loadJiraServer(String id) {
    Server server = settings.getServer(id);
    if (server != null) {
      getLog().debug("Found server " + id + " in settings: username=" + server.getUsername() + ", password="
          + server.getPassword());
      if (jiraUserName == null) {
        jiraUserName = server.getUsername();
      }
      if (jiraPassword == null) {
        jiraPassword = server.getPassword();
      }
    } else {
      getLog().debug("Could not find server " + id + " in settings");
    }
  }
}