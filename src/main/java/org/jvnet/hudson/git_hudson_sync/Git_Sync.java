package org.jvnet.hudson.git_hudson_sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class Git_Sync {

	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		if(args.length!=2)
			System.out.println("Usage: java <class> <base job> <git repo path>");
		else
			try {
				syncHudsonJobs(args[0], args[1]);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	@SuppressWarnings("unchecked")
	public static void syncHudsonJobs(String urlAddr, String gitDir) throws IOException, Exception
	{
		URL url = urlAddr==null?new URL("http://127.0.0.1:8081/hudson/api/xml"):new URL(urlAddr);
        Document dom = new SAXReader().read(url);
        
		File file=gitDir==null?new File("/Users/wlicpsc/Documents/Projects/Kashoo/books/.git"):new File(gitDir);
		RepositoryBuilder builder=new RepositoryBuilder();
		Repository repo;
		repo=builder.setGitDir(file).readEnvironment().findGitDir().build();
		Git git=new Git(repo);
		ListBranchCommand lbCommand=git.branchList();
		List<Ref> branchList=lbCommand.call();
		RevWalk walk=new RevWalk(repo);
		Date now=new Date();
		Pattern pattern=Pattern.compile("#[0-9]+");
		Matcher gitMatcher;
		String branchNumber = null;
		for(Ref item:branchList)
		{
			System.out.println(item.getName());
			System.out.println(item.toString()+", "+item.getObjectId().name());
			if(item.getName().contains("staging"))
				branchNumber="staging";
			else if(item.getName().contains("master"))
				branchNumber="master";
			else if(item.getName().contains("list"))
				branchNumber="list";
			else
			{
				gitMatcher=pattern.matcher(item.getName());
				while(gitMatcher.find())
				{
					System.out.println(gitMatcher.group(0));
					branchNumber=gitMatcher.group(0).substring(1);
				}
			}
			RevCommit commit=walk.parseCommit(item.getObjectId());
			Date commitTime=new Date((long)commit.getCommitTime()*1000);
			System.out.println(commitTime+", "+((now.getTime()-commitTime.getTime())/(24*60*60*1000)>30?"Old":"Recent"));
			if((now.getTime()-commitTime.getTime())/(24*60*60*1000)>30)	//if the date is not within 30 days
			{
		        for( Element job : (List<Element>)dom.getRootElement().elements("job")) {
		            System.out.println(job.elementText("name"));
		            if(branchNumber.equals(job.elementText("name")))
		            {
		            	//delete the job
		            	deleteJob(branchNumber);
		            }
		        }
			}
			else
			{
				boolean exists=false;
				for( Element job : (List<Element>)dom.getRootElement().elements("job"))
				{
					if(branchNumber.equals(job.elementText("name")))
					{
						exists=true;
						break;
					}
				}
				if(exists==false)
				{
					generateConfigFromTemplate(null, branchNumber);
					addJob(branchNumber);
				}
			}
		}
	}
	
	public static boolean generateConfigFromTemplate(File file, String branchId)
	{
		if(file==null)
			file=new File("configTemplate.xml");
		try
		{
		FileInputStream fis=new FileInputStream(file);
		BufferedReader in = new BufferedReader(new InputStreamReader(fis));
		String line="", templateText="";
		while((line=in.readLine())!=null)
		{
			templateText+=line+"\r\n";				
		}
		in.close();
		String newFileText=templateText.replaceFirst("<name>\\*\\*</name>", "<name>"+branchId+"</name>");
		FileWriter writer=new FileWriter("job_"+branchId+".xml");
		writer.write(newFileText);
		writer.close();
		System.out.println("Success changed and written the file");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static boolean addJob(String branchNumber)
	{
		boolean outcome = false;
		File input=new File("job_"+branchNumber+".xml");
		input.renameTo(new File("config.xml"));
		input=new File("config.xml");
		PostMethod post=new PostMethod("http://127.0.0.1:8081/hudson/createItem?name="+branchNumber);
		HttpClient httpclient = new HttpClient();
		try
		{
			post.setRequestEntity(new InputStreamRequestEntity(new FileInputStream(input), input.length()));
			post.setRequestHeader("Content-type", "text/xml; charset=UTF-8");
			int result=httpclient.executeMethod(post);
			System.out.println("Response status code: " + result);
			System.out.println("Response body: ");
            System.out.println(post.getResponseBodyAsString());
            if(result>=400)
            	outcome=true;
		} catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			post.releaseConnection();
		}
		if(outcome==true)
			input.delete();
		else
			System.out.println("Error adding the job #"+branchNumber);
		return outcome;
	}
	
	public static boolean deleteJob(String branchNumber)
	{
		boolean outcome = false;
		PostMethod post=new PostMethod("http://127.0.0.1:8081/hudson/job/TestAutoPostJob1/doDelete");
		HttpClient httpclient = new HttpClient();
		try
		{
			int result=httpclient.executeMethod(post);
			System.out.println("Response status code: " + result);
			System.out.println("Response body: ");
            System.out.println(post.getResponseBodyAsString());
            if(result<400)
            	outcome=true;
		} catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			post.releaseConnection();
		}
		if(outcome==false)
			System.out.println("Failed to delete job #"+branchNumber);
		return outcome;
	}

}
