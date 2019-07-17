package strafford_SOM_PKG.straff_Features.featureCalc;

import base_SOM_Objects.som_examples.SOM_FtrDataBoundMonitor;

/**
 * this class will manage a single data bound multi-dim array, consisting of 
 * per-jp arrays for min, max, diff, count, etc.
 * idxs are as follows : 1st idx is what type of examples are being aggregated; 2nd idx is min/max/diff, etc; last example is jp idx in source data (ironically this is going ot 
 * @author john
 *
 */
public class Straff_DataBoundMonitor extends SOM_FtrDataBoundMonitor{	
	//first dim of bndsAra
	//separates calculations based on whether calc is done on a customer example, or on a true prospect example
//	public static final int 
//		custCalcObjIDX 		= 0,		//type of calc : this is data aggregated off of customer data (has prior order events and possibly other events deemed relevant) - this corresponds to training data source records
//		tpCalcObjIDX 		= 1,		//type of calc : this is data from true prospect, who lack prior orders and possibly other event behavior
//		trainCalcObjIDX		= 2;		//type of calc : actual training data, based on date of orders - 1 or more of these examples will be synthesized for each customer prospect
//		//ttlOfAllCalcIDX		= 3;		//aggregate totals across all types - managed in base class
//	private static final int numExampleTypes = 3;
	
	public Straff_DataBoundMonitor(int _numExampleTypes, int _numElems) {
		super(_numExampleTypes, _numElems);
	}//ctor
}//class dataBoundArray



