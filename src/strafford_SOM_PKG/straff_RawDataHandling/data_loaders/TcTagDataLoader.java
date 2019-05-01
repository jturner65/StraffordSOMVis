package strafford_SOM_PKG.straff_RawDataHandling.data_loaders;

import strafford_SOM_PKG.straff_RawDataHandling.BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.TcTagData;

//stream tcTagdata to build product examples
public class TcTagDataLoader extends StraffordDataLoader{
	public TcTagDataLoader(boolean _isFileLoader, String _dataLocInfoStr) {super(_isFileLoader,_dataLocInfoStr);}
	@Override
	protected BaseRawData parseStringToObj(String[] strAra, String jsonStr, boolean hasJSON) {
		TcTagData obj = new TcTagData(msgObj,strAra[0].trim(), jsonStr.trim(),jsonMapper,hasJSON);
		obj.procInitData(strAra);
		return obj;
	}
}//
