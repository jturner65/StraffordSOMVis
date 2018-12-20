package SOM_Strafford_PKG;

import java.io.File;
import java.util.Calendar;

//structure to hold all the file names, file configurations and general program configurations required to run the SOM project
//will manage that all file names need to be reset when any are changed
public class SOMProjConfigData {
	//owning map manager
	public SOMMapManager mapMgr;
	
	private String[] fnames; 
	private static final int numFiles = 5;	
	
	
	//calendar object to be used to query instancing time
	private final Calendar instancedNow;
	//os used by project - this is passed from map
	private final String OSUsed;
	//calcFileWeights is file holding config for calc object - get from config file
	private static final String calcWtFileName = "WeightEqConfig.csv";
	//source directory for reading all source csv files, writing intermediate outputs, executing SOM_MAP and writing results
	private String straffBasefDir;
	//name of som executable
	private final String SOMExeName_base = "straff_SOM";

	//short name to be used in file names to denote this project
	private final String SOMProjName = "straff";
	//string to invoke som executable - platform dependent
	private final String SOM_Map_EXECSTR;

	// file IO/location constructs - TODO should these be loaded from config file?
	//subdir to put preproc data files
	public static final String straffPreProcSubDir = "PreprocData" + File.separator;
	//subdir to hold source csv files
	public static final String straffSourceCSVSubDir = "source_csvs" + File.separator;
	//subdir for all sql info - connect config file
	public static final String straffSQLProcSubDir = "Sql"+ File.separator;
	//subdir for all SOM functionality
	public static final String straffSOMProcSubDir = "SOM"+ File.separator;
	//subdir holding calc object information
	public static final String straffCalcInfoSubDir = "Calc"+ File.separator;
	//actual directory where SOM data is located
	public String SOMDataDir;	
	
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
	//idx 0 = file name for SOM training data -> .lrn file, 1 = file name for sphere sample data -> .csv file	
	private String[] SOMFileNamesAra;		

	
	public SOMProjConfigData(SOMMapManager _map) {
		mapMgr=_map;
		//----accumulate and manage OS info ----//
		//find platform this is executing on
		OSUsed = System.getProperty("os.name");
		mapMgr.dispMessage("SOMMapManager","Constructor","OS this application is running on : "  + OSUsed);
		//set invoking string for map executable - is platform dependent
		String execStr = SOMExeName_base;
		if (OSUsed.toLowerCase().contains("windows")) {
			execStr += ".exe";	
		}
		SOM_Map_EXECSTR = execStr;				
		//----end accumulate and manage OS info ----//		
		String baseDir = "StraffordProject" + File.separator;
		try {
			straffBasefDir = new File(baseDir).getCanonicalPath() + File.separator ;
		} catch (Exception e) {
			straffBasefDir =  baseDir;
			mapMgr.dispMessage("SOMProjConfigData","Constructor","Failed to find base application directory "+ baseDir + " due to : " + e);
		}
		mapMgr.dispMessage("SOMProjConfigData","Constructor","Canonical Path to application directory : " + straffBasefDir);
		mapMgr.dispMessage("SOMProjConfigData","Constructor","Loading Project configuration file. (Not yet implemented : TODO : build project config to facilitate turnkey operation.)");	
		
		
		instancedNow = Calendar.getInstance();
		fnames = new String[numFiles];
		for(int i=0;i<numFiles;++i){fnames[i]="";}
		initFlags();
	}
	
	public Calendar getInstancedNow() {return instancedNow;}
	
	//build a date with each component separated by token
	private String[] getDateTimeString(Calendar now){return getDateTimeString(false,".", now);}
	private String[] getDateTimeString(boolean toSecond, String token,Calendar now){
		String res, resWithYear="", resWithoutYear="";
		int val;
		val = now.get(Calendar.YEAR);	
		resWithYear += ""+val+token;
		val = now.get(Calendar.MONTH)+1;		res = (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;		
		val = now.get(Calendar.DAY_OF_MONTH);	res = (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;
		val = now.get(Calendar.HOUR_OF_DAY);	res = (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;
		val = now.get(Calendar.MINUTE);			res = (val < 10 ? "0"+val : ""+val);	 resWithYear += res;		resWithoutYear += res;		
		if(toSecond){val = now.get(Calendar.SECOND);res = token + (val < 10 ? "0"+val : ""+val)+ token;	 resWithYear += res;		resWithoutYear += res;}
		
		return new String[] {resWithYear,resWithoutYear};
	}
	
	//call when src data are first initialized - sets file names for .lrn file  and testing file output database query
	//dataFrmt : format used to train SOM == 0:unmodded; 1:std'ized; 2:normed
	public void setSOM_ExpFileNames(int _numSmpls, int _numTrain, int _numTest){
		//enable these to be set manually based on passed "now"
		String[] dateTimeStrAra = getDateTimeString(false, "_", instancedNow);//idx 0 has year, idx 1 does not
		String nowDirTmp = straffSOMProcSubDir+ "StraffSOM_"+dateTimeStrAra[0]+File.separator;
		String fileNow = dateTimeStrAra[1];
		setSOM_ExpFileNames( _numSmpls, _numTrain, _numTest, fileNow, nowDirTmp);		
	}//setSOM_ExpFileNames
	
	private void setSOM_ExpFileNames(int _numSmpls, int _numTrain, int _numTest, String fileNow, String nowDirTmp){
		String nowDir = getDirNameAndBuild(nowDirTmp);
		String dType = mapMgr.getDataTypeNameFromCurFtrType();
		String[] tmp = {"Out_"+SOMProjName+"_Smp_"+_numSmpls,
						"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" +dType + "_Dt_"+fileNow+".lrn",
						"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" + dType+"_Dt_"+fileNow+".svm",				
						"Test_"+SOMProjName+"_Smp_"+_numTest+"_of_"+_numSmpls+"_typ_" +dType +"_Dt_"+fileNow+".svm",
						"Mins_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
						"Diffs_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
						"SOMImg_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".png",
						"SOM_EXP_Format_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".txt"
						};
		SOMFileNamesAra = new String[tmp.length];
		
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			SOMFileNamesAra[i] = nowDir + tmp[i];
			mapMgr.dispMessage("SOMMapManager","setSOM_ExpFileNames","Built Dir : " + SOMFileNamesAra[i]);
		}				
	}//setSOM_ExpFileNames
	
	public String getFullCalcInfoFileName(){ return this.getDirNameAndBuild(straffCalcInfoSubDir) + calcWtFileName;}
	//file name to save record of bad events
	public String getBadEventFName(String dataFileName) { return straffBasefDir + dataFileName +"_bad_OIDs.csv";	}
	
	//set names to be pre-calced results to test map
	public void setStraffNamesDBG(String fileNow, int _numSmpls, int _numTrain, int _numTest, int _dataFrmt){ //StraffSOM_2018_11_09_14_45_DebugRun
		String nowDir = getDirNameAndBuild(straffSOMProcSubDir+"StraffSOM_2018_"+fileNow+"_DebugRun"+File.separator);
		String dType = mapMgr.getDataTypeNameFromCurFtrType();
		String[] tmp = {"Out_"+SOMProjName+"_Smp_"+_numSmpls,
				"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" +dType + "_Dt_"+fileNow+".lrn",
				"Train_"+SOMProjName+"_Smp_"+_numTrain+"_of_"+_numSmpls+"_typ_" + dType+"_Dt_"+fileNow+".svm",				
				"Test_"+SOMProjName+"_Smp_"+_numTest+"_of_"+_numSmpls+"_typ_" +dType +"_Dt_"+fileNow+".svm",
				"Mins_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
				"Diffs_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".csv",
				"SOMImg_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+"_typ_" +dType +".png",
				"SOM_EXP_Format_"+SOMProjName+"_Smp_"+_numSmpls+"_Dt_"+fileNow+".txt"
				};

		SOMFileNamesAra = new String[tmp.length];
		for(int i =0; i<SOMFileNamesAra.length;++i){	
			//build dir as well
			SOMFileNamesAra[i] = nowDir + tmp[i];
		}				
	}//setSOM_ExpFileNames	
	
	
	//moved from map manager
	public void setCurDataDir() {
		SOMDataDir = getDirNameAndBuild(straffSOMProcSubDir);
	}
	
	//get location for raw data files
	public String getRawDataLoadInfo(boolean fromFiles, String baseFName) {
		String dataLocStrData = "";
		if (fromFiles) {
			dataLocStrData = straffBasefDir + straffSourceCSVSubDir + baseFName + File.separator + baseFName+".csv";
		} else {//SQL connection configuration needs to be determined/designed
			dataLocStrData = straffBasefDir + straffSQLProcSubDir + "sqlConnData_"+baseFName+".csv";
			mapMgr.dispMessage("SOMMapManager","getLoadRawDataStrs","Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData);
		}
		return dataLocStrData;

	}//getRawDataLoadInfo
	
	//build prospect data directory structures based on current date
	public String[] buildProccedDataCSVFNames(boolean eventsOnly, String _desSuffix) {
		String[] dateTimeStrAra = getDateTimeString(false, "_", instancedNow);
		String subDir = "preprocData_" + dateTimeStrAra[0] + File.separator;
		return buildProccedDataCSVFNames(subDir, eventsOnly,_desSuffix);
	}//buildPrspctDataCSVFNames
	
	//build the file names for the csv files used to save intermediate data from db that has been partially preprocessed
	public String[] buildProccedDataCSVFNames(String subDir, boolean eventsOnly, String _desSuffix) {
		//build root preproc data dir if doesn't exist
		String rootDestDir = getDirNameAndBuild(straffPreProcSubDir);
		//build subdir based on date, if doesn't exist
		String destDir = getDirNameAndBuild(rootDestDir, subDir);
		String suffix;
		if (eventsOnly) {	suffix = _desSuffix + "Evnts";}
		else {				suffix = _desSuffix;}		
		String destForPrspctFName = destDir + suffix;				
		return new String[] {destForPrspctFName, suffix, rootDestDir};
	}//buildPrspctDataCSVFNames	
	
	//this will retrieve a subdirectory name under the main directory of this project and build the subdir if it doesn't exist
	//subdir assumed to have file.separator already appended (might not be necessary)
	public String getDirNameAndBuild(String subdir) {return getDirNameAndBuild(straffBasefDir,subdir);} 
	//baseDir must exist already
	public String getDirNameAndBuild(String baseDir, String subdir) {
		String dirName = baseDir +subdir;
		File directory = new File(dirName);
	    if (! directory.exists()){ directory.mkdir(); }
		return dirName;
	}//getDirNameAndBuild
	
	public String getOSUsed() {return OSUsed;}
	public String getSOM_Map_EXECSTR() {return SOM_Map_EXECSTR;}	
	
	public String getSOMProjName() { return SOMProjName;}
	public String getSOM_FullExecPath() { return getDirNameAndBuild(straffSOMProcSubDir);}
	
	public String getSOMMapOutFileBase(String sffx) { return SOMFileNamesAra[0] + sffx;}
	public String getSOMMapLRNFileName(){	return SOMFileNamesAra[1];	}
	public String getSOMMapSVMFileName(){	return SOMFileNamesAra[2];	}
	public String getSOMMapTestFileName(){	return SOMFileNamesAra[3];	}
	public String getSOMMapMinsFileName(){	return SOMFileNamesAra[4];	}
	public String getSOMMapDiffsFileName(){	return SOMFileNamesAra[5];	}	
	
	public String getSOMLocClrImgForJPFName(int jpIDX) {return "jp_"+mapMgr.jpJpgrpMon.getJpByIdx(jpIDX)+"_"+SOMFileNamesAra[6];}	
	//ref to file name for experimental setup
	public String getSOMMapExpFileName() {return SOMFileNamesAra[7];	}	
	
	
	//sets all file names to be loaded - assumes names to be valid
	public void setAllFileNames(boolean _useSparseDataFormat, String _somResBaseFName){
		String somCSVBaseFName = _somResBaseFName + "_outCSV";
		
		String diffsFileName = getSOMMapDiffsFileName(),	//diffs data percol .csv file
				minsFileName = getSOMMapMinsFileName();		//mins data per col csv
		/**
		 * load data to represent map results
		 * @param cFN class file name
		 * @param diffsFileName per-feature differences file name
		 * @param minsFileName per-feature mins file name
		 * @param lrnFileName som training data file name 
		 * @param outFilePrfx
		 * @param csvOutBaseFName file name prefix used to save class data in multiple formats to csv files
		 */
		String _trainDataFName = (_useSparseDataFormat) ?  getSOMMapSVMFileName() : getSOMMapLRNFileName();
		setFileName(trainDatFNameIDX, _trainDataFName);
		setFileName(somResFPrfxIDX, _somResBaseFName);
		setFileName(diffsFNameIDX, diffsFileName);
		setFileName(minsFNameIDX, minsFileName);
		setFileName(csvSavFNameIDX, somCSVBaseFName);
	}//setAllFileNames
	
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
	
	//public String getTrainFName(){return fnames[trainDatFNameIDX];	}
	//public String getTestFName(){return fnames[csvSavFNameIDX];	}
//	public String getDiffsFName(){return fnames[diffsFNameIDX];	}
//	public String getMinsFName(){return fnames[minsFNameIDX]; }
	
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
	private boolean getFlag(int idx){int bitLoc = 1<<(idx%32);return (stFlags[idx/32] & bitLoc) == bitLoc;}		
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
