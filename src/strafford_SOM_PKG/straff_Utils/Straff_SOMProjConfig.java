package strafford_SOM_PKG.straff_Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import base_SOM_Objects.som_managers.SOM_MapManager;
import base_SOM_Objects.som_utils.SOM_ProjConfigData;
import base_Utils_Objects.io.messaging.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

public class Straff_SOMProjConfig extends SOM_ProjConfigData {
	//type of event membership that defines a prospect as a customer and as a true prospect (generally will be cust has order event, prospect doesnt)
	protected int custTruePrsTypeEvents;
	//file name to use to save strafford-specific SOM config data
	private final String custStraffSOMConfigDataFileName= "Strafford_SOM_CustomMapTrainData.txt";

	public Straff_SOMProjConfig(SOM_MapManager _mapMgr, Map<String, Object> _argsMap) {super(_mapMgr, _argsMap);}

	//get location for raw data files
	//baseDirName : directory/file type name
	//baseFName : specific file base name (without extension) - will be same as baseDirName unless multiple files for specific file type were necessary for raw data due to size of data set
	@Override
	public String getRawDataLoadInfo(boolean fromFiles, String baseDirName, String baseFName) {
		String dataLocStrData = "";
		if (fromFiles) {
			dataLocStrData = SOM_QualifiedDataDir + subDirLocs.get("SOM_SourceCSV") + baseDirName + File.separator + baseFName+".csv";
		} else {//SQL connection configuration needs to be determined/designed
			dataLocStrData = SOM_QualifiedDataDir + subDirLocs.get("SOM_SQLProc") + "sqlConnData_"+baseDirName+".csv";
			msgObj.dispMessage("Straff_SOMProjConfig","getRawDataLoadInfo","Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData, MsgCodes.warning2);
		}
		return dataLocStrData;
	}//getRawDataLoadInfo
	
	@Override
	protected void _loadIndivConfigVarsPriv(String varName, String val) {
		switch (varName) {
			//add more variables here in instancing class - use string rep of name in config file, followed by a comma, followed by the string value (may include 2xquotes (") around string;) then can add more cases here
			case "custTruePrsTypeEvents": 	{	custTruePrsTypeEvents = Integer.parseInt(val);		break;}
		}
	}//_loadIndivConfigVarsPriv

	//return int representing type of events that should be used to define a prospect as a customer (generally has a order event in history) and a true prospect (lacks orders but has sources)
	public int getTypeOfEventsForCustAndProspect(){		return custTruePrsTypeEvents;}//
	
	/**
	 * get the project-specific per-map detail data used to display information regarding prebuilt maps
	 * adds weight calc file used
	 */
	@Override
	protected String[] getPreBuiltMapInfoStr_Indiv(ArrayList<String> res, String _preBuiltMapDir) {
		String fullQualPreBuiltMapDir = getDirNameAndBuild(subDirLocs.get("SOM_MapProc") + _preBuiltMapDir, true);
		//System.out.println("getPreBuiltMapInfoStr_Indiv : " + fullQualPreBuiltMapDir);
		String custCalcInfoFileName = fullQualPreBuiltMapDir + getSOMExpCustomConfigFileName_Indiv();
		TreeMap<String,String> custSOMTrainInfo =  getSOMExpConfigData(custCalcInfoFileName,"Custom SOM Config Data for Strafford");
		res.add(custSOMTrainInfo.get("calcWtFileName"));
		
		return res.toArray(new String[0]);
	}//getPreBuiltMapInfoStr_Indiv

	
	
	//////////////////
	// this is to manage saving and loading pre-mapped examples 
	//TODO this needs to be implemented
	//the premapped examples can be used for multiple products and will be valid as long as the same map is used
	
//	//return desired subdirectory to use to get mapped custs and true prospects data TODO not yet saving these
//	public String getMappedExDataDesToMapSubDirName(boolean _forceDefault) { return getDesExCSVDataSubDir("SOM_MappedExData", "SOM_MappedExDataSrc", _forceDefault);}
//	//SOM_MappedExData - this is for saving mapped examples - builds subdirectory within proc data 
//	public String[] buildMappedExDataCSVFnames_Save(String _desSuffix) { return buildCSVFileNamesAra("mappedExData_" + getDateTimeString(false, "_")[0] + File.separator, _desSuffix,"SOM_MappedExData");}
//	/**
//	 * Build the file names for the csv files used to load mapped data - example data that has been mapped to map nodes - this returns the directory we wish to load from
//	 * @param subDir sub directory to load from
//	 * @param _desSuffix text string describing file type
//	 * @return array with 3 elements holding [destination file name, _desSuffix (being returned), rootDestDir]
//	 */	
//	public String[] buildMappedExDataCSVFNames_Load(String subDir, String _desSuffix) {	return buildCSVFileNamesAra(subDir, _desSuffix,"SOM_MappedExData");}	
	
//	//return subdirectory to use to write results for product with passed OID
//	//THIS IS THE OLD VERSION AND WILL BE DEPRECATED
//	public String getPerProdOutSubDirName(String fullBaseDir,  String OID) {return getDirNameAndBuild(fullBaseDir, OID+File.separator, true);}
//	public String getFullProdOutMapperBaseDir(String sfx) {
//		String [] tmpNow = getDateTimeString(false, "_");
//		return getDirNameAndBuild(subDirLocs.get("SOM_ProdSuggest") + "PerProdMaps_"+sfx+"_"+ tmpNow[1] + "_data_" +dateTimeStrAra[0]+File.separator, true);
//	}
	
	@Override
	protected String getSegmentFileNamePrefix_Indiv(String segType, String fNamePrefix) {
		switch(segType.toLowerCase()) {
			case "nonprod_jps"  		: { return _getSegmentDirNameFromDirKey("Straff_SOM_NonProdJps","");}
			case "nonprod_jpgroups" 	: { return _getSegmentDirNameFromDirKey("Straff_SOM_NonProdJpGroups","");}
			default : {
				msgObj.dispMessage("Straff_SOMProjConfig","getSegmentFileNamePrefix_Indiv","Unknown Segment Type " +segType.toLowerCase() +" so unable to build appropriate file name prefix.  Aborting.", MsgCodes.warning2);
				return "";
			}
		}
	}//getSegmentFileName_Indiv
	
	/**
	 * Save instance-specific information regarding the SOM experiment 
	 */
	@Override
	protected final ArrayList<String> buildCustSOMConfigData() {
		ArrayList<String> customData = new ArrayList<String>();
		customData.add("# This file holds Strafford-specific configuration data pertaining to the training of the SOM in this directory");
		customData.add("# This data is here to override any default configuration data that may be loaded in from the project config file.");
		customData.add("#");
		customData.add("#\tThis is the name of the weight calc file used to train the SOM.  This file must be found in the subdirectory");
		customData.add("#\t"+SOM_QualifiedConfigDir + subDirLocs.get("StraffCalcEqWtFiles"));
		customData.add("#");
		customData.add("calcWtFileName,"+configFileNames.get("calcWtFileName"));
		customData.add("#");
		return customData;	
	}//buildCustSOMConfigData

	/**
	 * file name of custom config used to save instance-specific implementation details/files 
	 * @return file name of config file for custom config variables, under SOM Exec dir
	 */
	@Override
	protected final String getSOMExpCustomConfigFileName_Indiv() {	return custStraffSOMConfigDataFileName;}
	
	/**
	 * Instance-specific loading necessary for proper consumption of pre-built SOM
	 */
	@Override
	protected final void setSOM_UsePreBuilt_Indiv() {
		String custConfigSOMFileName = getSOMExpCustomConfigFileName();		
		String[] fileStrings = fileIO.loadFileIntoStringAra(custConfigSOMFileName, "Custom application-specific SOM Exp Config File loaded", "Custom application-specific SOM Exp Config File Failed to load");
		int idx = 0; boolean found = false;
		//find start of first block of data - 
		while (!found && (idx < fileStrings.length)){if(fileStrings[idx].contains(fileComment)){++idx; }  else {found=true;}}
		//idx here is for first non-comment field
		while (idx < fileStrings.length) {
			if((fileStrings[idx].contains(fileComment)) || (fileStrings[idx].trim().length()==0)){++idx; continue;}
			String[] tkns = fileStrings[idx].trim().split(SOM_MapManager.csvFileToken);
			String val = tkns[1].trim().replace("\"", "");
			String varName = tkns[0].trim();
			switch (varName) {		
				case "calcWtFileName" : {configFileNames.put("calcWtFileName",val);	break;}		//load the wt calc file name used to train this map
			}//switch
			++idx;
		}//while
	}//setSOM_UsePreBuilt_Indiv	
	
	/**
	 * This will save any application-specific reporting data by adding to arraylist
	 * @param reportData
	 */
	@Override
	protected final void saveSOM_ExecReport_Indiv(ArrayList<String> reportData) {
		//strafford-specific data to save
		reportData.add("Calc Object file location and name :"+ getFullCalcInfoFileName());
		reportData.add("Calc Object Config : " + ((Straff_SOMMapManager)mapMgr).ftrCalcObj.toString());
	}//saveSOM_ExecReport_Indiv
		
	//these file names are specified via a config file 
	public String getFullCalcInfoFileName(){ return SOM_QualifiedConfigDir + subDirLocs.get("StraffCalcEqWtFiles") + configFileNames.get("calcWtFileName");}
	//
	//not using this currently - keeping in case we wish to add the functionality to take existing bmu<-> prospects and bmu <-> features/ order jps/order jpgroups and link them 
	//public String getFullProdOutMapperInfoFileName(){ return SOM_QualifiedConfigDir + configFileNames.get("reqProdConfigFileName");}	

}//Straff_SOMProjConfig
