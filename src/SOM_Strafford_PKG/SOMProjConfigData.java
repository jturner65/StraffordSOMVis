package SOM_Strafford_PKG;

//structure to hold all the file names, file configurations and general program configurations required to run the SOM project
//will manage that all file names need to be reset when any are changed
public class SOMProjConfigData {
	public SOMMapManager map;
	private String[] fnames; 
	private static final int numFiles = 5;		
	//TODO
	//info required for raw data read from csv/db and preprocessing
	
	
	//info required to save/load preproced data to be converted to map-usable format
	
	
	//info required to build map experiment - should be savable and buildable/loadable from file configuration
	
	//info required to read results of map experiment and convert to UI vis, if used
	
	
	//boolean flags
	private int[] stFlags;						//state flags - bits in array holding relevant process info
	private static final int 
		trainDatFNameIDX	= 0,				//training data file name has been set
		somResFPrfxIDX		= 1,				//som results file prefix has been set
		diffsFNameIDX		= 2,				//file name holding per-ftr max-min differences in training data
		minsFNameIDX		= 3,				//file name holding per ftr mins in training data
		csvSavFNameIDX		= 4,				//file name prefix holding class saving file prefixs
		isDirty				= 5;				//current fnames is dirty - not all files are in sync
		//useCSVforTrainIDX	= 6;				//use .csv files for training data, otherwise uses .lrn files
	private static final int numFlags = 6;		
	
	public static final String[] SOMResExtAra = new String[]{".wts",".fwts",".bm",".umx"};			//extensions for different SOM output file types
	//idxs of different kinds of SOM output files
	public static final int
		wtsIDX = 0,
		fwtsIDX = 1,
		bmuIDX = 2,
		umtxIDX = 3;	
	//all flags corresponding to file names required to run SOM
	private int[] reqFileNameFlags = new int[]{trainDatFNameIDX, somResFPrfxIDX, diffsFNameIDX, minsFNameIDX, csvSavFNameIDX};
	private String[] reqFileNames = new String[]{"trainDatFNameIDX", "somResFPrfxIDX", "diffsFNameIDX", "minsFNameIDX", "csvSavFNameIDX"};

	public SOMProjConfigData(SOMMapManager _map) {
		map=_map;
		fnames = new String[numFiles];
		for(int i=0;i<numFiles;++i){fnames[i]="";}
		initFlags();
	}
	
	//sets all file names - assumes names to be valid
	public void setAllFileNames(String _dFN, String _mnFN, String _trainDataFName, String _somResBaseFName, String _somCSVBaseFName){
		setFileName(trainDatFNameIDX, _trainDataFName);


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
	//return true if all required files are loaded
	public boolean allReqFilesLoaded(){for(int i=0;i<reqFileNameFlags.length;++i){if(!getFlag(reqFileNameFlags[i])){return false;}}return true;}
	
	public String getTrainFName(){return fnames[trainDatFNameIDX];	}
	public String getTestFName(){return fnames[csvSavFNameIDX];	}
	public String getDiffsFName(){return fnames[diffsFNameIDX];	}
	public String getMinsFName(){return fnames[minsFNameIDX]; }
	
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
		String res = "SOM Project Data Config Cnstrct : \n";
		res += "\ttrainDatFNameIDX = " + fnames[trainDatFNameIDX]+"\n";
		res += "\tsomResFNameIDX = " + fnames[somResFPrfxIDX]+"\n";
		res += "\tdiffsFNameIDX = " + fnames[diffsFNameIDX]+"\n";
		res += "\tminsFNameIDX = " + fnames[minsFNameIDX]+"\n";
		res += "\tcsvSavFNameIDX = " + fnames[csvSavFNameIDX]+"\n";
		return res;	
	}

}
