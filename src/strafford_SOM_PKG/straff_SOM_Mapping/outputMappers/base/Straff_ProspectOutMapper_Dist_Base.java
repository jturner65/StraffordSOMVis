package strafford_SOM_PKG.straff_SOM_Mapping.outputMappers.base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_Utils_Objects.FileIOManager;
import base_Utils_Objects.MessageObject;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_SOM_Examples.products.ProductExample;

public abstract class Straff_ProspectOutMapper_Dist_Base implements Callable<Boolean>{
	protected MessageObject msgObj;
	protected FileIOManager fileIO;
	protected SOMExample[] prospectsToMap;
	protected int stIDX, endIDX, thdIDX;
	protected HashMap<ProductExample, HashMap<SOMMapNode, Double>> prodToMapNodes;
	protected String fileNameToSave, fileNameBadProspectsToSave;
	protected String callingClass;
	
	protected String outHdrStrHasMappings, outHdrStrHasNoMappings;

	public Straff_ProspectOutMapper_Dist_Base(MessageObject _msgObj, int _stIDX, int _endIDX, int _thdIDX, String _prspctType,  SOMExample[] _prospectsToMap, HashMap<ProductExample, HashMap<SOMMapNode, Double>> _prodToMapNodes,String _fullQualOutDir, String _childClass) {
		msgObj = _msgObj;	callingClass = _childClass;
		stIDX = _stIDX;		endIDX = _endIDX;		thdIDX = _thdIDX;	
		prospectsToMap = _prospectsToMap;	
		prodToMapNodes = _prodToMapNodes;
		fileNameToSave = getFileNameToSave(_fullQualOutDir,_prspctType);//_fullQualOutDir + _prspctType + "_mappingResults_" + thdIDX+".csv";
		fileNameBadProspectsToSave = _fullQualOutDir + _prspctType + "Prospect_WithoutMappings_" + thdIDX+".csv";
		fileIO = new FileIOManager(msgObj, "Straff_ProspectOutMapper_Dist_Base_"+thdIDX);
		setHdrStrings();
	}//ctor

	/**
	 * need to set 	outHdrStrHasMappings, outHdrStrHasNoMappings, used as headers for output files
	 */	
	protected abstract void setHdrStrings();
	protected abstract String getFileNameToSave(String _fullQualOutDir, String _prspctType);
	
	/**
	 * build list of output strings for each example
	 * @param strList array of strings for examples with mappings
	 * @param strListNoMaps array of strings of examples without mappings
	 * @param ex current example to map
	 */
	protected abstract void buildOutputLists(ArrayList<String> strList, ArrayList<String> strListNoMaps, SOMExample ex);
	
	@Override
	public final Boolean call() {
		//save all products from stIDX to endIDX
		msgObj.dispMessage("base->"+callingClass, "Run Thread : " +thdIDX, "Starting Prospect data to Product data to file from prospect IDs " +stIDX +" to "+ endIDX+".", MsgCodes.info5);
		ArrayList<String> strList = new ArrayList<String>(), strListNoMaps = new ArrayList<String>();	
		strList.add(outHdrStrHasMappings);
		strListNoMaps.add(outHdrStrHasNoMappings);
		String outRes;
		for (int i=stIDX; i<endIDX;++i) {
			buildOutputLists(strList,strListNoMaps, prospectsToMap[i]);
		}		
		msgObj.dispMessage("base->"+callingClass,"Call Thread : " +thdIDX,"Saving Per-Prospect Product Mapping suggestions : " +thdIDX, MsgCodes.info1);
		fileIO.saveStrings(fileNameToSave, strList);
		fileIO.saveStrings(fileNameBadProspectsToSave, strListNoMaps);
		msgObj.dispMessage("base->"+callingClass, "Call Thread : " +thdIDX, "Finished Prospect data to Product BMU mapping", MsgCodes.info5);	
		return true;
	}//call
	

}//class Straff_ProspectOutMapper_Dist_Base
