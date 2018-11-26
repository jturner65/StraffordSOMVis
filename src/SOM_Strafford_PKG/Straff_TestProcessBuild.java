package SOM_Strafford_PKG;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

//this class will manage a simple processbuilder execution of a specific executable in a mac environment
public class Straff_TestProcessBuild {

	public static void main(String[] args) {
		int numThreadsAvail = Runtime.getRuntime().availableProcessors();
		System.out.println("# threads : "+ numThreadsAvail);
		ExecutorService th_exec = Executors.newFixedThreadPool(numThreadsAvail);

		//array of execution parameters for executable process call - needs to be populated.
		String[] execString = new String[] {""};
		//absolute path to directory execution occurs in
		String wkDirStr = ""; 
	
		
		List<Future<Boolean>> procMsgMgrsFtrs = new ArrayList<Future<Boolean>>();
		List<messageMgr> procMsgMgrs = new ArrayList<messageMgr>(); 
		
		ProcessBuilder pb = new ProcessBuilder(execString);
		File wkDir = new File(wkDirStr); 
		pb.directory(wkDir);
		try {
			final Process process=pb.start();
			
			messageMgr inMsgs = new messageMgr(null, process,new InputStreamReader(process.getInputStream()), "Input" );
			messageMgr errMsgs = new messageMgr(null, process,new InputStreamReader(process.getErrorStream()), "Error" );
			procMsgMgrs.add(inMsgs);
			procMsgMgrs.add(errMsgs);
			
			procMsgMgrsFtrs = th_exec.invokeAll(procMsgMgrs);for(Future<Boolean> f: procMsgMgrsFtrs) { f.get(); }

			String resultIn = inMsgs.getResults(), resultErr = errMsgs.getResults() ;//result of running map TODO save to log?
			
			
			
		} catch (IOException e) {
	        // this code will be executed if a IOException happens "e.getMessage()" will have an error
			//e.printStackTrace();
			System.out.println("Process failed with IOException : " + e.toString() + "\n\t"+ e.getMessage());
	    } catch (InterruptedException e) {
			//e.printStackTrace();
			System.out.println("Process failed with InterruptedException : " + e.toString() + "\n\t"+ e.getMessage());	    	
	    } catch (ExecutionException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Process failed with ExecutionException : " + e.toString() + "\n\t"+ e.getMessage());
		}
		
		

	}//main
}//Straff_TestProcessBuild
//
////manage a message stream from a process
//class messageMgr implements Runnable {
//	final Process process;
//	BufferedReader rdr;
//	StringBuilder strbld;
//	String type;
//	public messageMgr(final Process _process, Reader _in, String _type) {
//		process=_process;
//		rdr = new BufferedReader(_in); 
//		strbld = new StringBuilder();
//		type=_type;
//	}
//	@Override
//	public void run() {
//		String sIn = null;
//		try {
//			while ((sIn = rdr.readLine()) != null) {
//				System.out.println(type+" Stream Msg : " + sIn);
//				strbld.append(sIn);			
//				strbld.append(System.getProperty("line.separator"));				
//			}
//		} catch (IOException e) {
//	        // this code will be executed if a IOException happens "e.getMessage()" will have an error
//			e.printStackTrace();
//			System.out.println("Process IO failed with exception : " + e.toString() + "\n\t"+ e.getMessage());
//		}
//	
//	}//run
//	
//}//messageMgr
