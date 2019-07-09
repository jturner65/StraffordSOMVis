package strafford_SOM_PKG.straff_SOM_Mapping.exampleManagers.base;

import java.util.*;
import java.util.concurrent.Future;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.SOM_Example;
import base_SOM_Objects.som_fileIO.SOM_ExCSVDataLoader;
import strafford_SOM_PKG.straff_Features.featureCalc.Straff_WeightCalc;
import strafford_SOM_PKG.straff_ProcDataHandling.data_loaders.Straff_CustCSVDataLoader;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.Straff_CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.Straff_SOMMapManager;

/**
 * base class to manage customer prospects - instanced by either per-customer training example manager or per-order training example manager.
 * @author john
 */
public abstract class Straff_SOMCustPrspctManager_Base extends Straff_SOMExampleManager {

	public Straff_SOMCustPrspctManager_Base(SOM_MapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {		super(_mapMgr, _exName, _longExampleName, new boolean[] {_shouldValidate, true});	}//ctor
	
	/**
	 * display debug information regarding customer order counts seen
	 */
	private final void dispAllNumOrderCounts() {
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","# of customers with particular order count : ");
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\t# of Unique JPs\t# of Customers with this many Unique Jps");
		int ttlOrders = 0;
		for(Integer numJPs : Straff_CustProspectExample.ttlOrderCount.keySet()) {
			Integer[] orderDat = Straff_CustProspectExample.ttlOrderCount.get(numJPs);
			msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\t"+numJPs+"\t\t"+orderDat[0]+"\t\t"+orderDat[1]);
			ttlOrders += orderDat[1];
		}
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\tTotal # of Orders across all customers : " + ttlOrders);
		// the # of customers considered "bad" after features were built
		msgObj.dispInfoMessage("Straff_SOMCustPrspctMapper","buildStraffFtrVec_Priv->dispAllNumOrderCounts","\tTotal # Of Customers considered 'bad' after features were built : " + Straff_CustProspectExample.NumBadExamplesAfterFtrsBuilt + " responsible for " + Straff_CustProspectExample.NumBadExampleOrdersAfterFtrsBuilt+" orders.  These examples shouldn't be used to train.");

		dbg_dispFtrVecMinMaxs(Straff_WeightCalc.mapOfTrainCompFtrVecMins, Straff_WeightCalc.mapOfTrainCompFtrVecMaxs, "Straff_SOMCustPrspctMapper");
	}//dispAllNumOrderCounts
	
	@Override
	protected final SOM_Example[] castArray(ArrayList<SOM_Example> tmpList) {	return (Straff_CustProspectExample[])(tmpList.toArray(new Straff_CustProspectExample[0]));}
	/**
	 * return customer prospect example array - this will an array of customerProspect records always
	 * this is here so that if per-order training data is generated via an instance of 
	 * Straff_SOMCustPrspctPerOrderMapper class this function will still work to retrieve original customerProspect examples                                                                       
	 * @return array of customerProspect examples
	 */
	public abstract Straff_CustProspectExample[] getCustProspectExamples();
	
	/**
	 * code to execute after examples have had ftrs prepared - this calculates feature vectors
	 */
	@Override
	protected final void buildStraffFtrVec_Priv() {
		//reset calc analysis objects before building feature vectors to enable new analytic info to be aggregateds
		Straff_CustProspectExample.NumBadExamplesAfterFtrsBuilt = 0;		//reset count of "bad" customer records, as reported by eq calculations (0-value ftr vec) - don't train on these guys
		Straff_CustProspectExample.NumBadExampleOrdersAfterFtrsBuilt=0;
			//clear out records of order counts
		Straff_CustProspectExample.ttlOrderCount.clear();
		Straff_CustProspectExample.ttlBadOrderCount.clear();

		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(Straff_WeightCalc.custCalcObjIDX);	
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.resetCalcObjs(Straff_WeightCalc.trainCalcObjIDX);	//also reset training data calc for order-based training data
		
		//call to buildFeatureVector for all examples
		//forcing to be single threaded for this calculation
		mapMgr._ftrVecBuild(exampleMap.values(),0,exampleName, true);	
		
		//manage per-order calculations
		//display order information after features were built
		dispAllNumOrderCounts();
		
		msgObj.dispInfoMessage("Straff_SOMCustPrspctManager_Base","buildPrspctFtrVecs : " + exampleName + " Examples","Begin Setting Non-Product Jp Eqs training ftr vectors from Customer examples.");
		//these calls to initAllEqsForCustNonTrainCalc and finalizeAllEqsCustForNonTrainCalc manage for each non-product 
		//jp the exemplar ftr vector that most closely described their data - this will then be applied to each true prospect 
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.initAllEqsForCustNonTrainCalc();	
		//build per-non-prod jp ftr vector contribution
		for (SOM_Example ex : exampleMap.values()) {		((Straff_CustProspectExample) ex).buildNonProdJpFtrVec();}
		
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finalizeAllEqsCustForNonTrainCalc();	
		msgObj.dispInfoMessage("Straff_SOMCustPrspctManager_Base","buildPrspctFtrVecs : " + exampleName + " Examples","Finished Setting Non-Product Jp Eqs training ftr vectors from Customer examples.");			
		
		//call to _ftrVecBuild() with _typeOfProc==1 calls postFtrVecBuild for all examples of specified type 
		//strafford data doesn't currently use this functionality so we can comment this call
		//mapMgr._ftrVecBuild(exs, 1, exType);	
		//set calc/calc analysis state as finished
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(Straff_WeightCalc.custCalcObjIDX);	
		((Straff_SOMMapManager)mapMgr).ftrCalcObj.finishFtrCalcs(Straff_WeightCalc.trainCalcObjIDX);	
	}//buildFtrVec_Priv()
	
	/**
	 * code to execute after examples have had ftrs calculated - this will calculate std features and any alternate ftr mappings if used
	 */
	@Override
	protected void buildAfterAllFtrVecsBuiltStructs_Priv() {
		//call to buildFeatureVector for all examples to perform -finalization- after all feature vectors of this type have been built
		mapMgr._ftrVecBuild(exampleMap.values(),2,exampleName, true);	
	}
	
	@Override
	//manage multi-threaded loading
	protected final void buildMTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		List<Future<Boolean>> preProcLoadFtrs = new ArrayList<Future<Boolean>>();
		List<SOM_ExCSVDataLoader> preProcLoaders = new ArrayList<SOM_ExCSVDataLoader>();
		for (int i=0; i<numPartitions;++i) {	preProcLoaders.add(new Straff_CustCSVDataLoader(mapMgr, i, loadSrcFNamePrefixAra[0]+"_"+i+".csv",  exampleName+ " Data file " + i +" of " +numPartitions + " loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load", exampleMap));}	
		try {preProcLoadFtrs = th_exec.invokeAll(preProcLoaders);for(Future<Boolean> f: preProcLoadFtrs) { 			f.get(); 		}} catch (Exception e) { e.printStackTrace(); }					
	}//buildMTLoader
	
	@Override
	//manage single threaded loading
	protected final void buildSTLoader(String[] loadSrcFNamePrefixAra, int numPartitions) {
		for (int i=numPartitions-1; i>=0;--i) {
			String dataFile = loadSrcFNamePrefixAra[0]+"_"+i+".csv";
			String[] csvLoadRes = fileIO.loadFileIntoStringAra(dataFile,  exampleName+ " Data file " + i +" of " +numPartitions +" loaded",  exampleName+ " Data File " + i +" of " +numPartitions +" Failed to load");
			//ignore first entry - header
			for (int j=1;j<csvLoadRes.length; ++j) {
				String str = csvLoadRes[j];
				int pos = str.indexOf(',');
				String oid = str.substring(0, pos);
				Straff_CustProspectExample ex = new Straff_CustProspectExample(mapMgr, oid, str);
				exampleMap.put(oid, ex);			
			}
		}
	}//buildSTLoader
	
	@Override
	protected final SOM_Example buildSingleExample(String _oid, String _str) {	return new Straff_CustProspectExample(mapMgr, _oid, _str);}


	/**
	 * return array of examples to save their bmus - called from saveExampleBMUMappings in Straff_SOMExampleManager
	 * @return
	 */
	@Override
	protected final SOM_Example[] getExToSave() {
		buildExampleArray();					//force rebuilding
		Straff_CustProspectExample[] exToSaveBMUs = getCustProspectExamples();		
		msgObj.dispInfoMessage("Straff_SOMCustPrspctManager_Base","getExToSave","Size of exToSaveBMUs : " + exToSaveBMUs.length);
		return exToSaveBMUs;
	};

	
}//Straff_SOMCustPrspctMapper
