package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.JpgrpDescData;

//stream jpg data for descriptions
public class JpgrpDataLoader extends Straff_RawDataLoader{ 
	public JpgrpDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		JpgrpDescData obj = new JpgrpDescData(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
