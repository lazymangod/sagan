package sagan.projects.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import sagan.blog.PostFormat;
import sagan.blog.support.FormatAwarePostContentRenderer;
import sagan.projects.Project;
import sagan.projects.ProjectRelease;
import sagan.projects.ProjectSample;
import sagan.support.nav.Navigation;
import sagan.support.nav.Section;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.*;

/**
 * Controller that handles administrative actions for Spring project metadata, e.g. adding
 * new releases, updating documentation urls, etc. Per rules in
 * {@code sagan.SecurityConfig}, authentication is required for all requests. See
 * {@link ProjectsController} for public, read-only operations.
 */
@Controller
@RequestMapping("/admin/projects")
@Navigation(Section.PROJECTS) class ProjectAdminController {
    private static final List<String> CATEGORIES =
            Collections.unmodifiableList(Arrays.asList("incubator", "active", "attic", "community"));

    private ProjectMetadataService service;

    private final FormatAwarePostContentRenderer renderer;

    @Autowired
    public ProjectAdminController(ProjectMetadataService service, FormatAwarePostContentRenderer renderer) {
        this.service = service;
        this.renderer = renderer;
    }

    @RequestMapping(value = "", method = GET)
    public String list(Model model) {
        model.addAttribute("projects", service.getProjects());
        return "admin/project/index";
    }

    @RequestMapping(value = "new", method = GET)
    public String newProject(Model model) {
        ProjectRelease release = new ProjectRelease("1.0.0.BUILD-SNAPSHOT",
                ProjectRelease.ReleaseStatus.SNAPSHOT,
                false,
                "http://docs.spring.io/spring-new/docs/{version}/spring-new/htmlsingle/",
                "http://docs.spring.io/spring-new/docs/{version}/javadoc-api/",
                "org.springframework.new",
                "spring-new");

        Project project = new Project("spring-new",
                "New Spring Project",
                "http://github.com/spring-projects/spring-new",
                "http://projects.spring.io/spring-new",
                new ArrayList<>(Arrays.asList(release)),
                CATEGORIES.get(0));

        return edit(project, model);
    }

    @RequestMapping(value = "{id}", method = GET)
    public String edit(@PathVariable String id, Model model) {
        Project project = service.getProject(id);
        return edit(project, model);
    }

    @RequestMapping(value = "{id}", method = DELETE)
    public String delete(@PathVariable String id, Model model) {
        service.delete(id);
        return "redirect:./";
    }

    private String edit(Project project, Model model) {
        if (project == null) {
            return "error/404";
        }

        denormalizeProjectReleases(project);

        List<ProjectRelease> releases = project.getProjectReleases();
        if (!releases.isEmpty()) {
            model.addAttribute("groupId", releases.get(0).getGroupId());
        }

        int nextAvailableSampleDisplayOrder = project.getProjectSamples()
                .stream()
                .mapToInt(ProjectSample::getDisplayOrder)
                .max()
                .orElse(0) + 1;

        model.addAttribute("project", project);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("projectSampleDisplayOrder", nextAvailableSampleDisplayOrder);
        return "admin/project/edit";
    }

    @RequestMapping(value = "{id}", method = POST)
    public String save(@Valid Project project,
                       @RequestParam(defaultValue = "") List<String> releasesToDelete,
                       @RequestParam(defaultValue = "") List<Integer> samplesToDelete,
                       @RequestParam String groupId,
                       @RequestParam(required = false) String parentId) {
        Iterator<ProjectRelease> iReleases = project.getProjectReleases().iterator();
        while (iReleases.hasNext()) {
            ProjectRelease release = iReleases.next();
            if ("".equals(release.getVersion()) || releasesToDelete.contains(release.getVersion())) {
                iReleases.remove();
            }
        }
        normalizeProjectReleases(project, groupId);

        String renderedBootConfig = this.renderer.render(project.getRawBootConfig(), PostFormat.ASCIIDOC);
        project.setRenderedBootConfig(renderedBootConfig);
        String renderedOverview = this.renderer.render(project.getRawOverview(), PostFormat.ASCIIDOC);
        project.setRenderedOverview(renderedOverview);

        if (parentId != null) {
            Project parentProject = service.getProject(parentId);
            project.setParentProject(parentProject);
        }

        project.setProjectSamples(
                project.getProjectSamples()
                        .stream()
                        .filter(ps -> !(ps.getTitle().isEmpty() || ps.getUrl().isEmpty()))
                        .filter(ps -> !samplesToDelete.contains(ps.getDisplayOrder()))
                        .collect(Collectors.toList())
        );

        service.save(project);

        return "redirect:" + project.getId();
    }

    private void normalizeProjectReleases(Project project, String groupId) {
        for (ProjectRelease release : project.getProjectReleases()) {
            if (groupId != null) {
                release.setGroupId(groupId);
            }
            release.replaceVersionPattern();
        }
    }

    private void denormalizeProjectReleases(Project project) {
        List<ProjectRelease> releases = new ArrayList<>();
        for (ProjectRelease release : project.getProjectReleases()) {
            releases.add(release.createWithVersionPattern());
        }
        project.setProjectReleases(releases);
    }

}
