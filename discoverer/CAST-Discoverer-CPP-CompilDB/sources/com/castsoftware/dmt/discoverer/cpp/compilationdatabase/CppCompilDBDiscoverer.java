package com.castsoftware.dmt.discoverer.cpp.compilationdatabase;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.castsoftware.dmt.engine.discovery.AdvancedProjectsDiscovererAdapter;
import com.castsoftware.dmt.engine.discovery.IProjectsDiscovererUtilities;
import com.castsoftware.dmt.engine.discovery.ProjectsDiscovererWrapper.ProfileOrProjectTypeConfiguration.LanguageConfiguration;
import com.castsoftware.dmt.engine.project.Profile.IReferencedContents;
import com.castsoftware.dmt.engine.project.Profile.ResourceReference;
import com.castsoftware.dmt.engine.project.Profile.SourceReference;
import com.castsoftware.dmt.engine.project.Project;
import com.castsoftware.util.logger.Logging;

/**
 * Basic discoverer for
 */
public class CppCompilDBDiscoverer extends AdvancedProjectsDiscovererAdapter
{
	String connectionPath;
	String directoryId;
	String relativeDirectoryPath;
	private final Stack<String> directoryIds;
	private final Stack<String> relativeDirectoryPaths;
	
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
    }

    @Override
    public void startTree(String packageId, String packageName, String packageType, String versionId, String connectionPath)
    {
    	super.startTree(packageId, packageName, packageType, versionId, connectionPath);
    	this.connectionPath = connectionPath;
    	//To simulate a real extraction
    	//this.connectionPath = "/usr1/soter";
    	//this.connectionPath = "/home/yle/msieve";
    	//this.connectionPath = "/home/yle/ffmpeg";
    }

    @Override
    public void startDirectory(String directoryId, String directoryName, String relativeDirectoryPath)
    {
    	super.startDirectory(directoryId, directoryName, relativeDirectoryPath);
    	this.directoryId = directoryId;
    	this.relativeDirectoryPath = relativeDirectoryPath;
    	this.directoryIds.push(directoryId);
    	this.relativeDirectoryPaths.push(relativeDirectoryPath);
    }

    @Override
    public void endDirectory()
    {
    	this.directoryIds.pop();
    	if (this.directoryIds.size() > 0)
    		this.directoryId = this.directoryIds.lastElement();
    	else
    		this.directoryId = null;

    	this.relativeDirectoryPaths.pop();
    	if (this.relativeDirectoryPaths.size() > 0)
    		this.relativeDirectoryPath = this.relativeDirectoryPaths.lastElement();
    	else
    		this.relativeDirectoryPath = null;
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
        if (fileName.equals("compile_commands.json"))
        {
            Project project = getProjectsDiscovererUtilities().createInitialProject(fileId, f.getName(), "dmtdevmicrosofttechno.CppProject", fileId, directoryId);
        	if (parseProjectFile(relativeDirectoryPath, content, project, getProjectsDiscovererUtilities()) && project != null)
        		getProjectsDiscovererUtilities().deleteProject(project.getId());
        }
    }
    
    private boolean parseProjectFile(String relativeFilePath, String content, Project project, IProjectsDiscovererUtilities projectsDiscovererUtilities)
    {
    	int cLanguageId = -1;
    	int cHeaderLanguage = -1;
    	int cPlusPlusLanguage = -1;
    	int cPlusPlusHeaderLanguage = -1;
    	int cFamilyNotCompilableLanguage = -1;

        for (LanguageConfiguration languageConfiguration : projectsDiscovererUtilities.getProjectTypeConfiguration(project.getType()).getLanguageConfigurations())
        {
        	int languageId = languageConfiguration.getLanguageId();
            if ("CLanguage".equals(languageConfiguration.getLanguageName()))
            {
            	cLanguageId = languageId;
            	//TODO: change API to access to the resource languages
            	//for (ResourceTypeConfiguration resourceTypeConfiguration : languageConfiguration.getResourceTypeConfigurations())
            	//{
            	//	if ("CHeaderLanguage".equals(resourceTypeConfiguration.getLanguageName()))
            	//		cHeaderLanguage = languageId;
            	//	}
            	//}
                cHeaderLanguage = 1;
                continue;
            }
            else if ("CPlusPlusLanguage".equals(languageConfiguration.getLanguageName()))
            {
            	cPlusPlusLanguage = languageId;
            	//TODO: change API to access to the resource languages
                cPlusPlusHeaderLanguage = 1;
                continue;
            }
            else if ("CFamilyNotCompilableLanguage".equals(languageConfiguration.getLanguageName()))
            {
            	cFamilyNotCompilableLanguage = languageId;
            	continue;
            }
        }
        if (cLanguageId == -1) 
        {
            Logging.managedError("cast.dmt.discover.cpp.compilationdatabase.missingLanguage","LNG","CLanguage");
        }
        if (cPlusPlusLanguage == -1)
        {
            Logging.managedError("cast.dmt.discover.cpp.compilationdatabase.missingLanguage","LNG","CPlusPlusLanguage");
        }
        if (cFamilyNotCompilableLanguage == -1)
        {
            Logging.managedError("cast.dmt.discover.cpp.compilationdatabase.missingLanguage","LNG","CFamilyNotCompilableLanguage");
        }

        String path = connectionPath;
        
        return ProjectFileScanner.scan(path, relativeFilePath, project, content, projectsDiscovererUtilities, cLanguageId, cHeaderLanguage, cPlusPlusLanguage, cPlusPlusHeaderLanguage, cFamilyNotCompilableLanguage);
    }
    
    @Override
    public boolean reparseProject(Project project, String projectContent, IReferencedContents contents,
        IProjectsDiscovererUtilities projectsDiscovererUtilities)
    {
    	int languageId = 1;
    	int languageHeaderId = 1;
    	List<String> includes = null;
    	Iterator<? extends SourceReference> s = project.getSourceReferences().iterator();
    	if (s.hasNext())
    		languageId = s.next().getLanguageId();
    		
        Iterator<? extends ResourceReference> resourceRefs = project.getResourceReferences().iterator();

        while (resourceRefs.hasNext())
        {
        	ResourceReference resourceRef = resourceRefs.next();
        	if (resourceRef.getLanguageId() == Project.PROJECT_LANGUAGE_ID) {
        		
	            String ref = resourceRef.getResolutionRef();
	        	String castpathContent = contents.getContent(ref);
	        	project.removeResourceReference(resourceRef.getReferenceId());
	            if (castpathContent != null)
		            includes = ProjectFileScanner.scanConfig(project, castpathContent, languageId, languageHeaderId);
	            break;
        	}
        }
        if (includes != null && includes.size() > 1)
        	for (String include : includes)
        	{
        		project.addDirectoryReference(include, languageId, languageHeaderId);
        	}
    	return false;
    }
}
