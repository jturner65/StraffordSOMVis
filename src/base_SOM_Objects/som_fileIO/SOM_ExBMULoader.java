package base_SOM_Objects.som_fileIO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

import base_SOM_Objects.som_examples.SOM_Example;
import base_SOM_Objects.som_examples.SOM_MapNode;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;


/**
 * This class will map the BMUs as determined by SOM training code (loaded form bmu file) to each 
 * training example - these bmus were not derived through distance calculations in this application, 
 * but from provided bmu file built during SOM training.
 * @author john
 *
 */
public class SOM_ExBMULoader implements Callable<Boolean>{
	//object that manages message displays on screen
	private MessageObject msgObj;
	//thread index
	int thdIDX;
	//whether or not we are using chi sq distance (normalized by variance per ftr)
	boolean useChiSqDist;
	//what kind of features used to train map (umodded, normalized, std'ized)
	int ftrTypeUsedToTrain;
	//should always be training, not necessary
	int typeOfEx;	
	//map of map nodes to arrays of examples that consider these map nodes their bmus - loaded in from file written by SOM training code
	//any particular map node will only exist in one map, so no map nodes will be concurrently modified by this structure
	HashMap<SOM_MapNode, ArrayList<SOM_Example>> bmusToExmpl;
	
	public SOM_ExBMULoader(MessageObject _msgObj, int _ftrTypeUsedToTrain, boolean _useChiSqDist, int _typeOfEx, HashMap<SOM_MapNode, ArrayList<SOM_Example>> _bmusToExmpl, int _thdIDX) {
		msgObj = _msgObj;
		ftrTypeUsedToTrain = _ftrTypeUsedToTrain;
		useChiSqDist =_useChiSqDist;
		thdIDX= _thdIDX;	
		typeOfEx = _typeOfEx;
		bmusToExmpl = _bmusToExmpl;
		int numExs = 0;
		for (SOM_MapNode tmpMapNode : bmusToExmpl.keySet()) {
			ArrayList<SOM_Example> exs = bmusToExmpl.get(tmpMapNode);
			numExs += exs.size();
		}		
		msgObj.dispMessage("SOMExBMULoader","ctor : thd_idx : "+thdIDX, "# of bmus to proc : " +  bmusToExmpl.size() + " # exs : " + numExs, MsgCodes.info2);
	}//ctor

	@Override
	public Boolean call() throws Exception {
		if (useChiSqDist) {		
			for (SOM_MapNode tmpMapNode : bmusToExmpl.keySet()) {
				ArrayList<SOM_Example> exs = bmusToExmpl.get(tmpMapNode);
				for(SOM_Example ex : exs) {ex.setTrainingExBMU_ChiSq(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addTrainingExToBMUs(ex,typeOfEx);	}
			}		
		} else {		
			for (SOM_MapNode tmpMapNode : bmusToExmpl.keySet()) {
				ArrayList<SOM_Example> exs = bmusToExmpl.get(tmpMapNode);
				for(SOM_Example ex : exs) {ex.setTrainingExBMU(tmpMapNode, ftrTypeUsedToTrain); tmpMapNode.addTrainingExToBMUs(ex,typeOfEx);	}
			}
		}	
		return true;
	}//run	
}//SOMExBMULoader