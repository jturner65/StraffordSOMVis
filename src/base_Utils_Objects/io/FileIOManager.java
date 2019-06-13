package base_Utils_Objects.io;

import java.io.*;
import java.util.*;

/**
 * This class will manage file IO
 * @author john
 *
 */

public class FileIOManager{
	//msg object interface
	protected MessageObject msg;
	//name of owning class of the instance of this object, for display
	protected String owner;
	
	public FileIOManager(MessageObject _msg, String _owner) {owner=_owner; msg=_msg;}	
	//write data to file
	public void saveStrings(String fname, String[] data) { saveStrings( fname, data, false);}
	public void saveStrings(String fname, String[] data, boolean append) {
		PrintWriter pw = null;
		try {
		     File file = new File(fname);
		     FileWriter fw = new FileWriter(file, append);
		     pw = new PrintWriter(fw);
		     for (int i=0;i<data.length;++i) { pw.println(data[i]);}
		     
		} catch (IOException e) {	e.printStackTrace();}
		finally {			if (pw != null) {pw.close();}}
	}//saveStrings
	public void saveStrings(String fname, ArrayList<String> data) { saveStrings( fname, data, false);}
	public void saveStrings(String fname, ArrayList<String> data, boolean append) {
		PrintWriter pw = null;
		try {
		     File file = new File(fname);
		     FileWriter fw = new FileWriter(file, append);
		     pw = new PrintWriter(fw);
		     for (int i=0;i<data.size();++i) { pw.println(data.get(i));}
		     
		} catch (IOException e) {	e.printStackTrace();}
		finally {			if (pw != null) {pw.close();}}
	}//saveStrings
	
	
	public String[] loadFileIntoStringAra(String fileName, String dispYesStr, String dispNoStr) {try {return _loadFileIntoStringAra(fileName, dispYesStr, dispNoStr);} catch (Exception e) {e.printStackTrace(); } return new String[0];}
	//stream read the csv file and build the data objects
	private String[] _loadFileIntoStringAra(String fileName, String dispYesStr, String dispNoStr) throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
		List<String> lines = new ArrayList<String>();
		String[] res = null;
	    //int line = 1, badEntries = 0;
		try {
		    inputStream = new FileInputStream(fileName);
		    sc = new Scanner(inputStream);
		    while (sc.hasNextLine()) {lines.add(sc.nextLine()); }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		    msg.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra",dispYesStr+"\tLength : " +  lines.size(), MsgCodes.info3);
		    res = lines.toArray(new String[0]);		    
		} catch (Exception e) {	
			e.printStackTrace();
			msg.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra","!!"+dispNoStr, MsgCodes.error3);
			res= new String[0];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//loadFileContents	
	
	//load into multiple arrays for multi-threaded processing
	public String[][] loadFileIntoStringAra_MT(String fileName, String dispYesStr, String dispNoStr, int numHdrLines, int numThds) {
		try {return _loadFileIntoStringAra_MT(fileName, dispYesStr, dispNoStr, numHdrLines, numThds);} 
		catch (Exception e) {e.printStackTrace(); } 
		return new String[0][];
	}
	//load files into multiple arrays for multi-threaded processing
	private String[][] _loadFileIntoStringAra_MT(String fileName, String dispYesStr, String dispNoStr, int numHdrLines, int numThds) throws IOException {		
		FileInputStream inputStream = null;
		Scanner sc = null;
		List<String>[] lines = new ArrayList[numThds];
		for (int i=0;i<numThds;++i) {lines[i]=new ArrayList<String>();	}
		String[][] res = new String[numThds+1][];
		String[] hdrRes = new String[numHdrLines];
		int idx = 0, count = 0;
		try {
		    inputStream = new FileInputStream(fileName);
		    sc = new Scanner(inputStream);
		    for(int i=0;i<numHdrLines;++i) {    	hdrRes[i]=sc.nextLine();   }		    
		    while (sc.hasNextLine()) {
		    	lines[idx].add(sc.nextLine()); 
		    	idx = (idx + 1)%numThds;
		    	++count;
		    }
		    //Scanner suppresses exceptions
		    if (sc.ioException() != null) { throw sc.ioException(); }
		    msg.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra_MT",dispYesStr+"\tLength : " +  count + " distributed into "+lines.length+" arrays.", MsgCodes.info1);
		    for (int i=0;i<lines.length;++i) {res[i] = lines[i].toArray(new String[0]);	 }
		    res[res.length-1]=hdrRes;
		} catch (Exception e) {	
			e.printStackTrace();
			msg.dispMessage("fileIOManager:"+owner, "_loadFileIntoStringAra_MT","!!"+dispNoStr, MsgCodes.error2);
			res= new String[0][];
		} 
		finally {
		    if (inputStream != null) {inputStream.close();		    }
		    if (sc != null) { sc.close();		    }
		}
		return res;
	}//_loadFileIntoStringAra_MT

}//class fileIOManager

