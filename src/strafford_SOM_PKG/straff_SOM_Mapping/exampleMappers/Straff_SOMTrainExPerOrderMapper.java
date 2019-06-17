package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.ArrayList;

import base_SOM_Objects.SOM_MapManager;
import base_SOM_Objects.som_examples.ExDataType;
import base_SOM_Objects.som_examples.SOMExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;
import strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers.base.Straff_SOMCustPrspctMapper_Base;

/**
 * Object to manage strafford-specific example mapping for customers, treating them each individually as a single training record
 * Overrides base Strafford Customer Prospect mapper just to provide per-training example functionality, otherwise identical
 * @author john
 */

public class Straff_SOMTrainExPerOrderMapper extends Straff_SOMCustPrspctMapper_Base  {
	//a reference to the array holding the customer prospect examples; 
	//this is necessary because the actual examples managed by this mapper are the individual per-order examples
	private CustProspectExample[] custProspectExamples;
	
	public Straff_SOMTrainExPerOrderMapper(SOM_MapManager _mapMgr, String _exName, String _longExampleName, boolean _shouldValidate) {super(_mapMgr, _exName, _longExampleName + " per Order examples.", _shouldValidate);}
	//specific reset functionality for these type of examples
	@Override
	protected final void reset_Priv() {
		mapMgr.resetTrainDataAras();
		custProspectExamples = new CustProspectExample[0];
	}//reset_Priv

	//this treats every customer's order as an individual example
	@Override
	protected final void validateAndAddExToArray(ArrayList<SOMExample> tmpList, SOMExample ex) {
		if(!ex.isBadExample()) {	tmpList.addAll(((CustProspectExample) ex).getSingleOrderTrainingExamples());}
	}//validateAndAddEx
	
	@Override
	//add example from map to array without validation - all per-order training examples
	protected final SOMExample[] noValidateBuildExampleArray() {
		ArrayList<SOMExample> tmpList = new ArrayList<SOMExample>();
		for (String key : exampleMap.keySet()) {tmpList.addAll(((CustProspectExample) exampleMap.get(key)).getSingleOrderTrainingExamples());}	
		return tmpList.toArray(new CustProspectExample[0]);
	}
	
	@Override
	/**
	 * after example array has been built, and specific funcitonality for these types of examples 
	 * This is necessary for this class to manage customer prospect examples themselves,which 
	 * will end up not otherwise being aggregated due to training data actually being the individual 
	 * orders from each customer
	 * @param validate whether data should be validated or not (to meet certain criteria for the SOM as training data)
	 */
	protected final void buildExampleArrayEnd_Priv(boolean validate) {
		if(validate) {
			ArrayList<SOMExample> tmpList = new ArrayList<SOMExample>();
			for (String key : exampleMap.keySet()) {
				SOMExample ex = exampleMap.get(key);
				if(!ex.isBadExample()) {tmpList.add(ex);}	
			}			
			custProspectExamples = tmpList.toArray(new CustProspectExample[0]);
		} 
		else {	custProspectExamples = exampleMap.values().toArray(new CustProspectExample[0]);}		
	}//buildExampleArrayEnd_Priv
	
	/**
	 * return customer prospect example array - this will an array of customerProspect records always
	 * this is here so that if per-order training data is generated via an instance of 
	 * Straff_SOMCustPrspctPerOrderMapper class this function will still work to retrieve original customerProspect examples                                                                       
	 * @return array of customerProspect examples
	 */
	@Override
	public final CustProspectExample[] getCustProspectExamples() {
		if((null==SOMexampleArray) ||(SOMexampleArray.length==0)) {	buildExampleArray();}
		return custProspectExamples;
	}

}//Straff_SOMCustPrspctPerOrderMapper
