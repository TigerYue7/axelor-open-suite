package com.axelor.apps.stock.interfaces.massstockmove;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.stock.db.MassStockMove;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.TrackingNumber;
import java.math.BigDecimal;

public interface MassStockMovableProduct {

  MassStockMove getMassStockMove();

  void setMassStockMove(MassStockMove massStockMove);

  Product getProduct();

  TrackingNumber getTrackingNumber();

  StockLocation getStockLocation();

  BigDecimal getCurrentQty();

  BigDecimal getMovedQty();

  StockMoveLine getStockMoveLine();

  void setStockMoveLine(StockMoveLine stockMoveLine);

  Unit getUnit();
}
