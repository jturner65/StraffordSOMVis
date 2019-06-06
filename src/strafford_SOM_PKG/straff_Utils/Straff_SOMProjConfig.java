package strafford_SOM_PKG.straff_Utils;

import java.io.File;
import java.util.TreeMap;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_utils.SOMProjConfigData;
import base_Utils_Objects.MsgCodes;

public class Straff_SOMProjConfig extends SOMProjConfigData {
	//type of event membership that defines a prospect as a customer and as a true prospect (generally will be cust has order event, prospect doesnt)
	protected int custTruePrsTypeEvents;

	public Straff_SOMProjConfig(SOMMapManager _mapMgr, TreeMap<String, Object> _argsMap) {super(_mapMgr, _argsMap);}

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
			msgObj.dispMessage("SOMProjConfigData","getLoadRawDataStrs","Need to construct appropriate sql connection info and put in text config file : " + dataLocStrData, MsgCodes.warning2);
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
	
	//////////////////
	// this is to manage saving and loading pre-mapped examples 
	//TODO this needs to be implemented
	//the premapped examples can be used for multiple products and will be valid as long as the same map is used
	
	//return desired subdirectory to use to get mapped custs and true prospects data TODO not yet saving these
	public String getMappedExDataDesToMapSubDirName(boolean _forceDefault) { return getDesExCSVDataSubDir("SOM_MappedExData", "SOM_MappedExDataSrc", _forceDefault);}
	//SOM_MappedExData - this is for saving mapped examples - builds subdirectory within proc data 
	public String[] buildMappedExDataCSVFnames_Save(String _desSuffix) { return buildCSVFileNamesAra("mappedExData_" + getDateTimeString(false, "_")[0] + File.separator, _desSuffix,"SOM_MappedExData");}
	/**
	 * Build the file names for the csv files used to load mapped data - example data that has been mapped to map nodes - this returns the directory we wish to load from
	 * @param subDir sub directory to load from
	 * @param _desSuffix text string describing file type
	 * @return array with 3 elements holding [destination file name, _desSuffix (being returned), rootDestDir]
	 */	
	public String[] buildMappedExDataCSVFNames_Load(String subDir, String _desSuffix) {	return buildCSVFileNamesAra(subDir, _desSuffix,"SOM_MappedExData");}	
	
	//return subdirectory to use to write results for product with passed OID
	public String getPerProdOutSubDirName(String fullBaseDir,  String OID) {return getDirNameAndBuild(fullBaseDir, OID+File.separator, true);}
	public String getFullProdOutMapperBaseDir(String sfx) {
		String [] tmpNow = getDateTimeString(false, "_");
		return getDirNameAndBuild(subDirLocs.get("SOM_ProdSuggest") + "PerProdMaps_"+sfx+"_"+ tmpNow[1] + "_data_" +dateTimeStrAra[0]+File.separator, true);
	}
		
	//these file names are specified above but may be modified/set via a config file in future
	public String getFullCalcInfoFileName(){ return SOM_QualifiedConfigDir + subDirLocs.get("StraffCalcEqWtFiles") + File.separator + configFileNames.get("calcWtFileName");}
	//
	public String getFullProdOutMapperInfoFileName(){ return SOM_QualifiedConfigDir + configFileNames.get("reqProdConfigFileName");}	

}//Straff_SOMProjConfig
