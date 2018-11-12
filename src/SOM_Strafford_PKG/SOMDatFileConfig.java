package SOM_Strafford_PKG;

//structure to hold all the file names and file configurations required to load the SOM data and save the derived SOM info
//will manage that all file names need to be reset when any are changed
public class SOMDatFileConfig {
	public SOMMapData map;
	private String[] fnames; 
	public static final int numFiles = 5;	

	private int[] stFlags;						//state flags - bits in array holding relevant process info
	public static final int 
		trainDatFNameIDX	= 0,				//training data file name has been set
		somResFPrfxIDX		= 1,				//som results file prefix has been set
		diffsFNameIDX		= 2,				//file name holding per-ftr max-min differences in training data
		minsFNameIDX		= 3,				//file name holding per ftr mins in training data
		csvSavFNameIDX		= 4,				//file name prefix holding class saving file prefixs
		isDirty				= 5,				//current fnames is dirty - not all files are in sync
		useCSVforTrainIDX	= 6;				//use .csv files for training data, otherwise uses .lrn files
	public static final int numFlags = 7;		
	
	public static String[] SOMResExtAra = new String[]{".wts",".fwts",".bm",".umx"};			//extensions for different SOM output file types
	//all flags corresponding to file names
	private int[] reqFileNameFlags = new int[]{trainDatFNameIDX, somResFPrfxIDX, diffsFNameIDX, minsFNameIDX, csvSavFNameIDX};
	private String[] reqFileNames = new String[]{"trainDatFNameIDX", "somResFPrfxIDX", "diffsFNameIDX", "minsFNameIDX", "csvSavFNameIDX"};

	public SOMDatFileConfig(SOMMapData _map) {
		map=_map;
		fnames = new String[numFiles];
		for(int i=0;i<numFiles;++i){fnames[i]="";}
		initFlags();
	}
	
	//sets all file names - assumes names to be valid
	public void setAllFileNames(String _dFN, String _mnFN, String _trainDataFName, String _somResBaseFName, String _somCSVBaseFName){
		setFileName(trainDatFNameIDX, _trainDataFName);
		setFlag(useCSVforTrainIDX, _trainDataFName.toLowerCase().contains(".csv"));
		map.dispMessage("Use CSV for train is set : "+getFlag(useCSVforTrainIDX)+" for string " + _trainDataFName);
		setFileName(somResFPrfxIDX, _somResBaseFName);
		setFileName(diffsFNameIDX, _dFN);
		setFileName(minsFNameIDX, _mnFN);
		setFileName(csvSavFNameIDX, _somCSVBaseFName);
	}
	
	private void setFileName(int _typ, String _val){
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
	
	public String getTrainFname(){return fnames[trainDatFNameIDX];	}
	public String getDiffsFname(){return fnames[diffsFNameIDX];	}
	public String getMinsFname(){return fnames[minsFNameIDX]; }
	
	public String getAllDirtyFiles(){
		String res = "";
		for(int i=0;i<reqFileNameFlags.length;++i){if(getFlag(reqFileNameFlags[i])){res += reqFileNames[i]+", ";}}
		return res;
	}
	
	
	public String getSOMResFName(int ext){return (fnames[somResFPrfxIDX] + SOMResExtAra[ext]);}
	
	private void initFlags(){stFlags = new int[1 + numFlags/32]; for(int i = 0; i<numFlags; ++i){setFlag(i,false);}}
	public void setFlag(int idx, boolean val){
		boolean oldVal = getFlag(idx);
		int flIDX = idx/32, mask = 1<<(idx%32);
		stFlags[flIDX] = (val ?  stFlags[flIDX] | mask : stFlags[flIDX] & ~mask);
		switch (idx) {//special actions for each flag
			case isDirty 				: {if((oldVal != val) && (val)){setDirty();}break;}	//if transition to true, run setDirty 				
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
		res += "\ttrainDatFNameIDX = " + fnames[trainDatFNameIDX]+"\n";
		res += "\tsomResFNameIDX = " + fnames[somResFPrfxIDX]+"\n";
		res += "\tdiffsFNameIDX = " + fnames[diffsFNameIDX]+"\n";
		res += "\tminsFNameIDX = " + fnames[minsFNameIDX]+"\n";
		res += "\tcsvSavFNameIDX = " + fnames[csvSavFNameIDX]+"\n";
		return res;	
	}

}
