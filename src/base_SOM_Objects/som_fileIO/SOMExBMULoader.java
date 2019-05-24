package base_SOM_Objects.som_fileIO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

import base_SOM_Objects.som_examples.SOMExample;
import base_SOM_Objects.som_examples.SOMMapNode;
import base_Utils_Objects.MsgCodes;
import base_Utils_Objects.MessageObject;

 
//load best matching units for each provided training example - 
//this is only called when loading bmus with training data
public class SOMExBMULoader implements Callable<Boolean>{
	//object that manages message displays on screen
	private MessageObject msgObj;

	int thdIDX;
	boolean useChiSqDist;
	int ftrTypeUsedToTrain;
	int typeOfEx;	//should always be training
	HashMap<SOMMapNode, ArrayList<SOMExample>> bmusToExmpl;
	public SOMExBMULoader(MessageObject _msgObj, int _ftrTypeUsedToTrain, boolean _useChiSqDist, int _typeOfEx, HashMap<SOMMapNode, ArrayList<SOMExample>> _bmusToExmpl, int _thdIDX) {
		msgObj = _msgObj;
		ftrTypeUsedToTrain = _ftrTypeUsedToTrain;
		useChiSqDist =_useChiSqDist;
		thdIDX= _thdIDX;	
		typeOfEx = _typeOfEx;
		bmusToExmpl = _bmusToExmpl;
		int numExs = 0;
		for (SOMMapNode tmpMapNode : bmusToExmpl.keySet()) {
			ArrayList<SOMExample> exs = bmusToExmpl.get(tmpMapNode);
			numExs += exs.size();
		}		
		msgObj.dispMessage("SOMExBMULoader","ctor : thd_idx : "+thdIDX, "# of bmus to proc : " +  bmusToExmpl.size() + " # exs : " + numExs, MsgCodes.info2);
	}//ctor

	@Override
	public Boolean call() throws Exception {
		if (useChiSqDist) {		
			for (SOMMapNode tmpMapNode : bmusToExmpl.keySet()) {
				ArrayList<SOMExample> exs = bmusToExmpl.get(tmpMapNode);
				for(SOMExample ex : exs) {ex.setTrainingExBMU_ChiSq(tmpMapNode, ftrTypeUsedToTrain);tmpMapNode.addTrainingExToBMUs(ex,typeOfEx);	}
			}		
		} else {		
			for (SOMMapNode tmpMapNode : bmusToExmpl.keySet()) {
				ArrayList<SOMExample> exs = bmusToExmpl.get(tmpMapNode);
				for(SOMExample ex : exs) {ex.setTrainingExBMU(tmpMapNode, ftrTypeUsedToTrain); tmpMapNode.addTrainingExToBMUs(ex,typeOfEx);	}
			}
		}	
		return true;
	}//run	
}//SOMExBMULoader