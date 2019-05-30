/**
 * 
 */
package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.*;
import java.util.concurrent.Future;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_fileIO.SOMExCSVDataLoader;
import base_Utils_Objects.MsgCodes;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.CustCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;
import strafford_SOM_PKG.straff_Utils.featureCalc.StraffWeightCalc;

/**
 * Object to manage strafford-specific example mapping for customers, treating them each individually as a single training record
 * @author john
 */
public class Straff_SOMCustPrspctMapper extends Straff_SOMProspectMapper {

	public Straff_SOMCustPrspctMapper(SOMMapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {		super(_mapMgr, _exName, _longExampleName, _shouldValidate);	}//ctor
	
	//specific reset functionality for these type of examples
	@Override
	protected void reset_Priv() {
		mapMgr.resetTrainDataAras();
	}//reset_Priv

	private void dispAllNumOrderCounts() {
		msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","# of customers with particular order count : ", MsgCodes.info1);
		msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\t# of Unique JPs\t# of Customers with this many Unique Jps",MsgCodes.info1);
		int ttlOrders = 0;
		for(Integer numJPs : CustProspectExample.ttlOrderCount.keySet()) {
			Integer[] orderDat = CustProspectExample.ttlOrderCount.get(numJPs);
			msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\t"+numJPs+"\t\t"+orderDat[0]+"\t\t"+orderDat[1],MsgCodes.info1);
			ttlOrders += orderDat[1];
		}
		msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\tTotal # of Orders across all customers : " + ttlOrders,MsgCodes.info1);
		// the # of customers considered "bad" after features were built
		msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs->dispAllNumOrderCounts","\tTotal # Of Customers considered 'bad' after features were built : " + CustProspectExample.NumBadExamplesAfterFtrsBuilt + ".  These examples shouldn't be used to train.",MsgCodes.info1);
	}//dispAllNumOrderCounts

	
	//this treats every customer's total past behavior as a single training example 
	@Override
	protected void validateAndAddExToArray(ArrayList<SOMExample> tmpList, SOMExample ex) {	if(!ex.isBadExample()) {tmpList.add(ex);}}//validateAndAddEx	//
	@Override
	//add example from map to array without validation
	protected SOMExample[] noValidateBuildExampleArray() {	return (CustProspectExample[])(exampleMap.values().toArray(new CustProspectExample[0]));};	
	@Override
	protected SOMExample[] castArray(ArrayList<SOMExample> tmpList) {	return (CustProspectExample[])(tmpList.toArray(new CustProspectExample[0]));}
	@Override
	//after example array has been built, and specific funcitonality for these types of examples
	protected void buildExampleArrayEnd_Priv(boolean validate) {}
	//return customer prospect example array - this is same as regular example array for these kinds of customers
	//this is here so that if per-order training data is generated via an instance of Straff_SOMCustPrspctPerOrderMapper class
	//this function will still work to retrieve examples
	public CustProspectExample[] getCustProspectExamples() {
		if((null==SOMexampleArray) ||(SOMexampleArray.length==0)) {	buildExampleArray();}
		return (CustProspectExample[]) SOMexampleArray;}

	
	/**
	 * code to execute after examples have had ftrs prepared - this calculates feature vectors
	 */
	@Override
	protected void buildStraffFtrVec_Priv() {
		//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregateds
		CustProspectExample.NumBadExamplesAfterFtrsBuilt = 0;		//reset count of "bad" customer records, as reported by eq calculations (0-value ftr vec) - don't train on these guys
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(StraffWeightCalc.custCalcObjIDX);	
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(StraffWeightCalc.trainCalcObjIDX);	//also reset training data calc for order-based training data
		
		//call to buildFeatureVector for all examples
		mapMgr._ftrVecBuild(exampleMap.values(),0,exampleName);	
		
		//manage per-order calculations
		//display order information after features were built
		dispAllNumOrderCounts();
		
		msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs : " + exampleName + " Examples","Begin Setting Non-Product Jp Eqs training ftr vectors from Customer examples.", MsgCodes.info1);
		//these calls to initAllEqsForCustNonTrainCalc and finalizeAllEqsCustForNonTrainCalc manage for each non-product 
		//jp the exemplar ftr vector that most closely described their data - this will then be applied to each true prospect 
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.initAllEqsForCustNonTrainCalc();	
		//build per-non-prod jp ftr vector contribution
		for (SOMExample ex : exampleMap.values()) {		((CustProspectExample) ex).buildNonProdJpFtrVec();}
		
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finalizeAllEqsCustForNonTrainCalc();	
		msgObj.dispMessage("StraffSOMMapManager","buildPrspctFtrVecs : " + exampleName + " Examples","Finished Setting Non-Product Jp Eqs training ftr vectors from Customer examples.", MsgCodes.info1);			
		
		//call to _ftrVecBuild() with _typeOfProc==1 calls postFtrVecBuild for all examples of specified type - strafford data doesn't currently use this functionality so we can comment this call
		//mapMgr._ftrVecBuild(exs, 1, exType);	
		//set calc/calc analysis state as finished
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(StraffWeightCalc.custCalcObjIDX);	
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(StraffWeightCalc.trainCalcObjIDX);	
	}//buildFtrVec_Priv()
	
	
	@Override
	//manage multi-threaded loading
	protected void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
		List<SOMExCSVDataLoader> preProcLoaders = new ArrayList<SOMExCSVDataLoader>();
		for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new CustCSVDataLoader(mapMgr, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  exampleName+ " Data file " + i +" of " +numPartitions + " loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load", exampleMap));}	
		try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { 			f.get(); 		}} catch (Exception e) { e.printStackTrace(); }					
	}//buildMTLoader
	
	@Override
	//manage single threaded loading
	protected void buildSTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		for (int i=numPartitions-1; i>=0;--i) {
			String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
			String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile,  exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load");
			//ignore first entry - header
			for (int j=1;j<csvLoadRes.length; ++j) {
				String str = csvLoadRes[j];
				int pos = str.indexOf(',');
				String oid = str.substring(0, pos);
				CustProspectExample ex = new CustProspectExample(mapMgr, oid, str);
				exampleMap.put(oid, ex);			
			}
		}
	}//buildSTLoader
	
	
}//Straff_SOMCustPrspctMapper
