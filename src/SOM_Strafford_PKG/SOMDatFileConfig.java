package SOM_Strafford_PKG;

//structure to hold all the file names and file configurations required to load the SOM data and save the derived SOM info
//will manage that all file names need to be reset when any are changed
public class SOMDatFileConfig {
	public SOMMapData map;
	private String[] fnames; 
	public static final int numFiles = 6;	

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int 
		classFNameIDX 		= 0,				//class file name has been set
		trainDatFNameIDX	= 1,				//training data file name has been set
		somResFPrfxIDX		= 2,				//som results file prefix has been set
		diffsFNameIDX		= 3,				//file name holding per-ftr max-min differences in training data
		minsFNameIDX		= 4,				//file name holding per ftr mins in training data
		csvSavFNameIDX		= 5,				//file name prefix holding class saving file prefixs
		isDirty				= 6,				//current fnames is dirty - not all files are in sync
		useCSVforTrainIDX	= 7;				//use .csv files for training data, otherwise uses .lrn files
	public static final int numFlags = 8;		
	
	public static String[] SOMResExtAra = new String[]{".wts",".fwts",".bm",".umx"};			//extensions for different SOM output file types
	//all flags corresponding to file names
	private int[] reqFileNameFlags = new int[]{classFNameIDX, trainDatFNameIDX, somResFPrfxIDX, diffsFNameIDX, minsFNameIDX, csvSavFNameIDX};
	private String[] reqFileNames = new String[]{"classFNameIDX", "trainDatFNameIDX", "somResFPrfxIDX", "diffsFNameIDX", "minsFNameIDX", "csvSavFNameIDX"};

	public SOMDatFileConfig(SOMMapData _map) {
		map=_map;
		fnames = new String[numFiles];
		for(int i=0;i<numFiles;++i){fnames[i]="";}
		initFlags();
	}
	
	//sets all file names - assumes names to be valid
	public void setAllFileNames(String _classFName, String _dFN, String _mnFN, String _trainDataFName, String _somResBaseFName, String _somCSVBaseFName){
		setFileName(classFNameIDX, _classFName);
		setFileName(trainDatFNameIDX, _trainDataFName);
		setFlag(useCSVforTrainIDX, _trainDataFName.toLowerCase().contains(".csv"));
		map.dispMessage("Use CSV for train is set : "+getFlag(useCSVforTrainIDX)+" for string " + _trainDataFName);
		setFileName(somResFPrfxIDX, _somResBaseFName);
		setFileName(diffsFNameIDX, _dFN);
		setFileName(minsFNameIDX, _mnFN);
		setFileName(csvSavFNameIDX, _somCSVBaseFName);
	}
	
	public void setFileName(int _typ, String _val){
		if(!fnames[_typ].equals(_val)){
			fnames[_typ] = _val;		//set appropriate file name
			setFlag(isDirty, true);		//"false"'s all flags for each file, if first time dirty is set
		}
		setFlag(_typ,true);		//flag idx is same as string idx
		setFlag(isDirty,!allReqFilesLoaded());	//should clear dirty flag when all files are loaded
	}//setFileName
	
	//call only 1 time, when setting isDirty to true - only run once per dirty flag being in true state
	private void setDirty(){for(int i=0;i<reqFileNameFlags.length;++i){setFlag(reqFileNameFlags[i],false);}}	
	//return true of all required files are loaded
	public boolean allReqFilesLoaded(){for(int i=0;i<reqFileNameFlags.length;++i){if(!getFlag(reqFileNameFlags[i])){return false;}}return true;}
	
	public String getClassFname(){return fnames[classFNameIDX];	}
	public String getTrainFname(){return fnames[trainDatFNameIDX];	}
	public String getDiffsFname(){return fnames[diffsFNameIDX];	}
	public String getMinsFname(){return fnames[minsFNameIDX]; }
	
	public String getAllDirtyFiles(){
		String res = "";
		for(int i=0;i<reqFileNameFlags.length;++i){if(getFlag(reqFileNameFlags[i])){res += reqFileNames[i]+", ";}}
		return res;
	}
	
	//return the appropriate file name based on the type of file being saved
	//_arg1 for file names with extra info, such as per-class data where the class is included in file name
	public String getCSVSvFName(int _typ, int _arg1, String sfx){ 	
		switch(_typ){
			case 0 : {return (fnames[csvSavFNameIDX] + "ScaledData_Classes" + sfx + ".csv");}//trainClassDataFName -> like MmntSOMSrcDir+"ScaledHeadData_Classes_useAll_0.csv"
			case 1 : {return (fnames[csvSavFNameIDX] + "ListClasses" + sfx + ".csv");}//trainClassDataFName -> like MmntSOMSrcDir+"ListClasses_useAll_0.csv"
			case 2 : {return (fnames[csvSavFNameIDX] + "SparseListClasses" + sfx + ".csv");}//trainClassDataFName -> like MmntSOMSrcDir+"SparseListClasses_useAll_0.csv"
			case 3 : {return (fnames[csvSavFNameIDX] + "PerClass" + sfx + "_class_"+_arg1 +".csv");}//trainClassDataFName -> like MmntSOMSrcDir+"PerClass_useAll_1_class_1.csv"
		}
		return "File Type Not Found";
	}
	
	public String getSOMResFName(int ext){return (fnames[somResFPrfxIDX] + SOMResExtAra[ext]);}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		boolean oldVal = getFlag(idx);
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case isDirty 				: {if((oldVal != val) && (val)){setDirty();}break;}	//if transition to true, run setDirty 				
			case classFNameIDX 			: {break;}//{p.pr("setClsFName set to val : " +val);break;}
			case trainDatFNameIDX		: {break;}//{p.pr("setDiffsFName set to val : " +val);break;}
			case somResFPrfxIDX			: {break;}//{p.pr("setMinsFName set to val : " +val);break;}
			case diffsFNameIDX			: {break;}//{p.pr("setTrainFName set to val : " +val);break;}
			case minsFNameIDX			: {break;}//{p.pr("setSomBaseResFName set to val : " +val);break;}
			case csvSavFNameIDX			: {break;}//{p.pr("setCSVSavBaseFName set to val : " +val);break;}
		}
	}//setFlag	
	public boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
	@Override
	public String toString(){
		String res = "FName Cnstrct : \n";
		res += "\tclassFNameIDX = " + fnames[classFNameIDX]+"\n";
		res += "\ttrainDatFNameIDX = " + fnames[trainDatFNameIDX]+"\n";
		res += "\tsomResFNameIDX = " + fnames[somResFPrfxIDX]+"\n";
		res += "\tdiffsFNameIDX = " + fnames[diffsFNameIDX]+"\n";
		res += "\tminsFNameIDX = " + fnames[minsFNameIDX]+"\n";
		res += "\tcsvSavFNameIDX = " + fnames[csvSavFNameIDX]+"\n";
		return res;	
	}

}
