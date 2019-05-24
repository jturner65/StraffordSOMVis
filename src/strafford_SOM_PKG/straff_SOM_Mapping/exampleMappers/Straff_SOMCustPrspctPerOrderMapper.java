package strafford_SOM_PKG.straff_SOM_Mapping.exampleMappers;

import java.util.ArrayList;

import base_SOM_Objects.SOMMapManager;
import base_SOM_Objects.som_examples.SOMExample;
import strafford_SOM_PKG.straff_SOM_Examples.prospects.CustProspectExample;

/**
 * Object to manage strafford-specific example mapping for customers, treating them each individually as a single training record
 * Overrides base Strafford Customer Prospect mapper just to provide per-training example functionality, otherwise identical
 * @author john
 */

public class Straff_SOMCustPrspctPerOrderMapper extends Straff_SOMCustPrspctMapper  {
	//a reference to the array holding the customer prospect examples; 
	//this is necessary because the actual examples managed by this mapper are the individual per-order examples
	private SOMExample[] custProspectExamples;
	
	public Straff_SOMCustPrspctPerOrderMapper(SOMMapManager _mapMgr, String _exName) {super(_mapMgr, _exName);}

	//this treats every customer's order as an individual example
	@Override
	protected void validateAndAddEx(ArrayList<SOMExample> tmpList, SOMExample ex) {
		if(!ex.isBadExample()) {	tmpList.addAll(((CustProspectExample) ex).getSingleOrderTrainingExamples());}		
	}//validateAndAddEx
	
	@Override
	//add example from map to array without validation - all per-order training examples
	protected SOMExample[] noValidateBuildExampleArray() {
		ArrayList<SOMExample> tmpList = new ArrayList<SOMExample>();
		for (String key : exampleMap.keySet()) {tmpList.addAll(((CustProspectExample) exampleMap.get(key)).getSingleOrderTrainingExamples());}	
		return tmpList.toArray(new CustProspectExample[0]);
	};	

	@Override
	//after example array has been built, and specific funcitonality for these types of examples
	//this is necessary for his class to manage customer prospect examples themselves, which will end up not being aggregated due to training data actually being the individual orders
	protected void buildExampleArrayEnd_Priv(boolean validate) {
		if(validate) {
			ArrayList<SOMExample> tmpList = new ArrayList<SOMExample>();
			for (String key : exampleMap.keySet()) {
				SOMExample ex = exampleMap.get(key);
				if(!ex.isBadExample()) {tmpList.add(ex);}	
			}			
			custProspectExamples = tmpList.toArray(new CustProspectExample[0]);
		} 
		else {	custProspectExamples = exampleMap.values().toArray(new CustProspectExample[0]);}		
	}

}//Straff_SOMCustPrspctPerOrderMapper
