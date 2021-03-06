package com.castsoftware.dmt.discoverer.cpp.compilationdatabase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.castsoftware.dmt.discoverer.cpp.compilationdatabase.CompileFile.IncludePath;
import com.castsoftware.dmt.discoverer.cpp.compilationdatabase.CompileFile.Macro;
import com.castsoftware.dmt.engine.discovery.AdvancedProjectsDiscovererAdapter;
import com.castsoftware.dmt.engine.discovery.IProjectsDiscovererUtilities;
import com.castsoftware.dmt.engine.discovery.ProjectsDiscovererWrapper.ProfileOrProjectTypeConfiguration.LanguageConfiguration;
import com.castsoftware.dmt.engine.project.IResourceReadOnly;
import com.castsoftware.dmt.engine.project.Profile.IReferencedContents;
import com.castsoftware.dmt.engine.project.Profile.Option;
import com.castsoftware.dmt.engine.project.Profile.ResourceReference;
import com.castsoftware.dmt.engine.project.Profile.SourceReference;
import com.castsoftware.dmt.engine.project.Project;
import com.castsoftware.util.logger.Logging;

/**
 * Basic discoverer for
 */
public class CppCompilDBDiscoverer extends AdvancedProjectsDiscovererAdapter
{
    private String connectionPath;
    private String directoryId;
    private String relativeDirectoryPath;
	private final Stack<String> directoryIds;
	private final Stack<String> relativeDirectoryPaths;
    private final List<String> jsonFiles;

    boolean isInitLanguages = false;
    static int cLanguageId = 1;
    static int cHeaderLanguage = 1;
    static int cPlusPlusLanguage = 2;
    static int cPlusPlusHeaderLanguage = 1;
    static int cFamilyNotCompilableLanguage = 3;
    
    static String META_COMPILE_CONFIG="compile_config";

    /**
     * Default constructor used by the discovery engine
     */
    public CppCompilDBDiscoverer()
    {
    	connectionPath = "";
    	directoryId = null;
    	relativeDirectoryPath = null;
    	directoryIds = new Stack<String>();
    	relativeDirectoryPaths = new Stack<String>();
        jsonFiles = new ArrayList<String>();
    }

    @Override
    public void startTree(String packageId, String packageName, String packageType, String versionId, String path)
    {
        super.startTree(packageId, packageName, packageType, versionId, path);
        connectionPath = path;
    }

    @Override
    public void startDirectory(String id, String directoryName, String path)
    {
        super.startDirectory(id, directoryName, path);
        directoryId = id;
    	relativeDirectoryPath = path;
        directoryIds.push(id);
    	relativeDirectoryPaths.push(path);
    }

    @Override
    public void endDirectory()
    {
    	directoryIds.pop();
    	if (directoryIds.size() > 0)
    		directoryId = directoryIds.lastElement();
    	else
    		directoryId = null;

    	relativeDirectoryPaths.pop();
    	if (relativeDirectoryPaths.size() > 0)
    		relativeDirectoryPath = relativeDirectoryPaths.lastElement();
    	else
    		relativeDirectoryPath = null;
    }

    @Override
    public boolean mustProcessFile(String fileName)
    {
    	return false;
        //return fileName.endsWith("compile_commands.json");
    }

    @Override
	public void startTextFile(String fileId, String fileName, long fileSize, String relativeFilePath, String content)
    {
    	File f = new File(connectionPath);
        if (fileName.equals("compile_commands.json") && !jsonFiles.contains(relativeFilePath))
        {
        	Logging.info("cast.dmt.discover.cpp.compilationdatabase.startProcessingCompileCommands", "FILE", relativeFilePath);
            jsonFiles.add(relativeFilePath);
            
            Project project = getProjectsDiscovererUtilities().createInitialProject(fileId, f.getName(), "dmtdevmicrosofttechno.CppProject", fileId, directoryId);
            parseProjectFile(relativeDirectoryPath, content, project, getProjectsDiscovererUtilities());
            if (project != null)
        		getProjectsDiscovererUtilities().deleteProject(project.getId());
        	Logging.info("cast.dmt.discover.cpp.compilationdatabase.endProcessingCompileCommands", "FILE", relativeFilePath);
        }
    }

    private void initLanguages(IProjectsDiscovererUtilities projectsDiscovererUtilities, String projectType)
    {
        if (!isInitLanguages)
        {
            isInitLanguages = true;
            for (LanguageConfiguration languageConfiguration : projectsDiscovererUtilities.getProjectTypeConfiguration(
                projectType).getLanguageConfigurations())
            {
                int languageId = languageConfiguration.getLanguageId();
                if ("CLanguage".equals(languageConfiguration.getLanguageName()))
                {
                    if (cLanguageId != languageId)
                        Logging.managedError("cast.dmt.discover.cpp.compilationdatabase.missingLanguage", "LNG", "CLanguage");
                    // TODO: change API to access to the resource languages
                    // for (ResourceTypeConfiguration resourceTypeConfiguration :
                    // languageConfiguration.getResourceTypeConfigurations())
                    // {
                    // if ("CHeaderLanguage".equals(resourceTypeConfiguration.getLanguageName()))
                    // cHeaderLanguage = languageId;
                    // }
                    // }
                    // cHeaderLanguage = 1;
                    continue;
                }
                else if ("CPlusPlusLanguage".equals(languageConfiguration.getLanguageName()))
                {
                    if (cPlusPlusLanguage != languageId)
                        Logging.managedError("cast.dmt.discover.cpp.compilationdatabase.missingLanguage", "LNG",
                            "CPlusPlusLanguage");
                    // TODO: change API to access to the resource languages
                    // cPlusPlusHeaderLanguage = 1;
                    continue;
                }
                else if ("CFamilyNotCompilableLanguage".equals(languageConfiguration.getLanguageName()))
                {
                    if (cFamilyNotCompilableLanguage != languageId)
                        Logging.managedError("cast.dmt.discover.cpp.compilationdatabase.missingLanguage", "LNG",
                            "CFamilyNotCompilableLanguage");
                    continue;
                }
            }
        }

        return;
    }

    private void parseProjectFile(String relativeFilePath, String content, Project project,
        IProjectsDiscovererUtilities projectsDiscovererUtilities)
    {
        initLanguages(projectsDiscovererUtilities, project.getType());

        List<CompileFile> compileFiles = new ArrayList<CompileFile>();
        List<CompileLink> compileLinks = new ArrayList<CompileLink>();

        if (!ProjectFileScanner.scan(connectionPath, relativeFilePath, project, content, compileFiles, compileLinks))
            createProjects(compileFiles, compileLinks, connectionPath, relativeFilePath, project, projectsDiscovererUtilities);
        return;

    }

    /**
     * Create the project defined in a compile_commands.json file
     *
     * @param compileFiles
     *            list of compileFile found in the json
     * @param compileLinks
     *            list of compileLink found in the json
     * @param connectionPath
     *            the project file interpreter
     * @param relativeFilePath
     *            the path to the project file used for reference
     * @param project
     *            the file content to scan.
     * @param projectsDiscovererUtilities
     *            utility to create the projects
     */
    public static void createProjects(List<CompileFile> compileFiles, List<CompileLink> compileLinks, String connectionPath,
        String relativeFilePath,
        Project project, IProjectsDiscovererUtilities projectsDiscovererUtilities)
    {
        // delete the default project and create one project per folder
        if (compileFiles.size() > 0)
        {
            if (compileLinks.size() > 0)
            {
                for (CompileLink compileLink : compileLinks)
                {
                    String id = project.getId() + "#" + compileLink.getLinkname();
                    compileLink.setCompileFiles(compileFiles);
                    Project p = projectsDiscovererUtilities
                            .createInitialProject(id, compileLink.getLinkname(), project.getType(), id, project.getPath());
                    // p.addMetadata(IResourceReadOnly.METADATA_REFKEY, folderName);
                    String fullPath = project.getPath().concat("/compile_config.json");
                    p.addFileReference(fullPath, Project.PROJECT_LANGUAGE_ID,
                        IResourceReadOnly.RESOURCE_TYPE_NEUTRAL_ID);
                    for (CompileFile cf : compileLink.getCompileFiles())
                    {
                        String fileRef = PathHelper
                                .getRelativeConnectionPath(p, connectionPath, null, null, cf.getFilename());
                        if (p.getFileReference(fileRef) == null)
                            p.addSourceFileReference(fileRef, cf.getLanguageId());

                        if (p.getMetadata(META_COMPILE_CONFIG) == null)
                            p.setMetadata(META_COMPILE_CONFIG, cf.getType());

                        for (Macro macro : cf.getMacros())
                            addMacro(p, macro.getKey(), macro.getValue());

                        for (IncludePath include : cf.getIncludes())
                        {
                            String includeRef = PathHelper.getRelativeConnectionPath(p, connectionPath, relativeFilePath,
                                cf.getDirectory(), include.getPath());
                            if (p.getDirectoryReference(includeRef) == null
                                && project.getResourceReference(includeRef) == null)
                            {
                                ResourceReference ref = p.addDirectoryReference(includeRef, cf.getLanguageId(), cf.getLanguageHeaderId());
                                if (include.getType() != null && !include.getType().equals("I"))
                                	ref.addMetadata("includeType", include.getType());
                            }
                        }
                    }
                }
            }
            else
            {
                Set<String> sourceFolders;
                // Map<String, String> commandSourceFolders = new HashMap<String, String>();
                sourceFolders = new HashSet<String>();

                for (CompileFile cf : compileFiles)
                {
                    //String folderRef = PathHelper.getRelativeConnectionPath(project, connectionPath, relativeFilePath,
                    //    cf.getFilename(), "");
                    String folderRef = cf.getFolderPath();
                    //if (!folderRef.startsWith("/") && folderRef.startsWith("%#!$?BASE_DIRECTORY_PATH"))
                    //    folderRef = folderRef.substring(folderRef.indexOf("/"));
                    //if (folderRef.lastIndexOf("/") == 0)
                    //    folderRef = ".";
                    //else
                    //{
                    //    folderRef = folderRef.substring(1);
                    //    folderRef = folderRef.substring(0, folderRef.lastIndexOf("/"));
                    //}
                    if (!sourceFolders.contains(folderRef))
                        sourceFolders.add(folderRef);
                    cf.setFolder(folderRef);
                }
                for (String folderRef : sourceFolders)
                {
                	String relativeFolderRef = folderRef;
                	if (folderRef.startsWith(connectionPath + "/"))
                		relativeFolderRef = folderRef.substring(connectionPath.length() + 1);
                    String id = project.getId() + "#" + relativeFolderRef;
                    int pos = relativeFolderRef.lastIndexOf("/") + 1;
                    String projectPath = project.getPath();
                    String projectName = project.getName();
                    if (".".equals(relativeFolderRef) || projectPath.equals(relativeFolderRef))
                    {
                        projectPath = project.getPath();
                        String folderName = projectPath.substring(projectPath.lastIndexOf("/") + 1);
                        projectName = folderName;
                    }
                    else if (relativeFolderRef.startsWith("/"))
                    {
                    	// specific case for which the compile_commands.json is in another folder
                        projectPath = relativeFolderRef;
                        String folderName = relativeFolderRef.substring(relativeFolderRef.lastIndexOf("/") + 1);
                        projectName = folderName;
                    }
                    else if (relativeFolderRef.length() > pos)
                    {
                    	if (".".equals(project.getPath()))
                    		projectPath = relativeFolderRef;
                    	else
                    		projectPath = project.getPath() + "/" + relativeFolderRef;
                        String folderName = folderRef.substring(folderRef.lastIndexOf("/") + 1);
                        projectName = folderName;
                    }
                    Project p = projectsDiscovererUtilities
                                .createInitialProject(id, projectName, project.getType(), id, projectPath);
                    //p.addMetadata(IResourceReadOnly.METADATA_REFKEY, projectName);
                    String fullPath = project.getPath().concat("/compile_config.json");
                    p.addFileReference(fullPath, Project.PROJECT_LANGUAGE_ID,
                        IResourceReadOnly.RESOURCE_TYPE_NEUTRAL_ID);

                    for (CompileFile cf : compileFiles)
                    {
                        if (cf.getFolder().equals(folderRef))
                        {
                            cf.setLinked(true);
                            String fileRef = PathHelper
                                    .getRelativeConnectionPath(p, connectionPath, null, null, cf.getFilename());
                            if (p.getFileReference(fileRef) == null)
                                p.addSourceFileReference(fileRef, cf.getLanguageId());

                            for (Macro macro : cf.getMacros())
                                addMacro(p, macro.getKey(), macro.getValue());

                            cf.transformIncludesInFullPath();
                            for (IncludePath include : cf.getIncludes())
                            {
                            	String includeRef = PathHelper.getRelativeConnectionPath(connectionPath, include.getPath());
                                if (includeRef != null
                                	&& p.getDirectoryReference(includeRef) == null
                                    && project.getResourceReference(includeRef) == null)
                                {
                                    ResourceReference ref = p.addDirectoryReference(includeRef, cf.getLanguageId(), cf.getLanguageHeaderId());
                                    if (include.getType() != null && !include.getType().equals("I"))
                                    	ref.addMetadata("includeType", include.getType());
                                }
                            }
                        }
                    }
                }
            }
            for (CompileFile cf : compileFiles)
            {
                if (!cf.isLinked())
                    Logging.warn("cast.dmt.discover.cpp.compilationdatabase.filenotlinked", "FILE", cf.getFilename());
            }
        }
        else
        {
            Logging.warn("cast.dmt.discover.cpp.compilationdatabase.noFile", "PATH", project.getPath() + "/compile_commands.json");
        }
        return;
    }

    private static void addMacro(Project project, String macroName, String macroValue)
    {
        Option o = project.getOption(macroName);
        if (o == null)
            project.addOption(macroName, macroValue);
        else
        {
            if (macroValue == null && o.getValue() != null)
                Logging.warn("cast.dmt.discover.cpp.compilationdatabase.macroconflict1", "MACRO", macroName, "VALUE", o.getValue());
            if (macroValue != null && !macroValue.equals(o.getValue()))
                Logging.warn("cast.dmt.discover.cpp.compilationdatabase.macroconflict2", "MACRO", macroName, "VALUE1", macroValue, "VALUE2", o.getValue());
        }
        return;
    }


    @Override
    public boolean reparseProject(Project project, String projectContent, IReferencedContents contents,
        IProjectsDiscovererUtilities projectsDiscovererUtilities)
    {
    	int languageId = 1;
    	int languageHeaderId = 1;
    	CompileConfig compileConfig = null;
    	Iterator<? extends SourceReference> s = project.getSourceReferences().iterator();
    	if (s.hasNext())
    		languageId = s.next().getLanguageId();

        Iterator<? extends ResourceReference> resourceRefs = project.getResourceReferences().iterator();

        // ESCA-JAVA0254:
        String configRef = null;
        while (resourceRefs.hasNext())
        {
        	ResourceReference resourceRef = resourceRefs.next();
        	if (resourceRef.getLanguageId() == Project.PROJECT_LANGUAGE_ID) {
	            String ref = resourceRef.getResolutionRef();
                configRef = ref;
	        	String castpathContent = contents.getContent(ref);
                if (castpathContent != null)
                {
                    if (project.getMetadata(META_COMPILE_CONFIG) != null)
                    {
                        String command = project.getMetadata(META_COMPILE_CONFIG).getValue();
                        compileConfig = ProjectFileScanner.scanConfig(command, ref, castpathContent);
                    }
                }
	            break;
        	}
        }
        if (compileConfig != null)
        {
        	Map<String, String> macros = compileConfig.getDefines();
            if (macros != null && macros.size() > 1)
                for (Map.Entry<String, String> macro : macros.entrySet())
                {
                	String macroName = macro.getKey();
            		String macroValue = macro.getValue();
                    Option o = project.getOption(macroName);
                    if (o == null)
                	{
                		project.addOption(macroName, macroValue);
                	}
                    else
                    {
                        if (macroValue == null && o.getValue() != null)
                            Logging.warn("cast.dmt.discover.cpp.compilationdatabase.macroconflict1", "MACRO", macroName, "VALUE", o.getValue());
                        if (macroValue != null && !macroValue.equals(o.getValue()))
                            Logging.warn("cast.dmt.discover.cpp.compilationdatabase.macroconflict2", "MACRO", macroName, "VALUE1", macroValue, "VALUE2", o.getValue());
                    }
                }
            List<String> includes = compileConfig.getInclude_paths();
            if (includes != null && includes.size() > 1)
                for (String include : includes)
                {
                    project.addDirectoryReference(include, languageId, languageHeaderId);
                }
        }
        if (configRef != null)
            project.removeResourceReference(configRef);
    	return false;
    }
}
