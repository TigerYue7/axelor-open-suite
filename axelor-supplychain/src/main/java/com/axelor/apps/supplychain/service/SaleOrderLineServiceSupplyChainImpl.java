/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2024 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.supplychain.service;

import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.purchase.db.SupplierCatalog;
import com.axelor.apps.purchase.service.app.AppPurchaseService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.apps.stock.service.StockLocationService;
import com.axelor.apps.supplychain.db.repo.SupplyChainConfigRepository;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.inject.Beans;
import com.axelor.utils.helpers.StringHelper;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.persistence.TypedQuery;

public class SaleOrderLineServiceSupplyChainImpl implements SaleOrderLineServiceSupplyChain {

  protected AppSupplychainService appSupplychainService;

  @Inject
  public SaleOrderLineServiceSupplyChainImpl(AppSupplychainService appSupplychainService) {
    this.appSupplychainService = appSupplychainService;
  }

  @Override
  public BigDecimal getAvailableStock(SaleOrder saleOrder, SaleOrderLine saleOrderLine) {

    StockLocationLine stockLocationLine =
        Beans.get(StockLocationLineService.class)
            .getStockLocationLine(saleOrder.getStockLocation(), saleOrderLine.getProduct());

    if (stockLocationLine == null) {
      return BigDecimal.ZERO;
    }
    return stockLocationLine.getCurrentQty().subtract(stockLocationLine.getReservedQty());
  }

  @Override
  public BigDecimal getAllocatedStock(SaleOrder saleOrder, SaleOrderLine saleOrderLine) {

    StockLocationLine stockLocationLine =
        Beans.get(StockLocationLineService.class)
            .getStockLocationLine(saleOrder.getStockLocation(), saleOrderLine.getProduct());

    if (stockLocationLine == null) {
      return BigDecimal.ZERO;
    }
    return stockLocationLine.getReservedQty();
  }

  @Override
  public BigDecimal computeUndeliveredQty(SaleOrderLine saleOrderLine) {
    Preconditions.checkNotNull(saleOrderLine);

    BigDecimal undeliveryQty = saleOrderLine.getQty().subtract(saleOrderLine.getDeliveredQty());

    if (undeliveryQty.signum() > 0) {
      return undeliveryQty;
    }
    return BigDecimal.ZERO;
  }

  @Override
  public List<Long> getSupplierPartnerList(SaleOrderLine saleOrderLine) {
    Product product = saleOrderLine.getProduct();
    if (!Beans.get(AppPurchaseService.class).getAppPurchase().getManageSupplierCatalog()
        || product == null
        || product.getSupplierCatalogList() == null) {
      return new ArrayList<>();
    }
    return product.getSupplierCatalogList().stream()
        .map(SupplierCatalog::getSupplierPartner)
        .filter(Objects::nonNull)
        .map(Partner::getId)
        .collect(Collectors.toList());
  }

  @Override
  public void updateDeliveryStates(List<SaleOrderLine> saleOrderLineList) {
    if (ObjectUtils.isEmpty(saleOrderLineList)) {
      return;
    }

    for (SaleOrderLine saleOrderLine : saleOrderLineList) {
      updateDeliveryState(saleOrderLine);
    }
  }

  @Override
  public void updateDeliveryState(SaleOrderLine saleOrderLine) {
    if (saleOrderLine.getDeliveredQty().signum() == 0) {
      saleOrderLine.setDeliveryState(SaleOrderLineRepository.DELIVERY_STATE_NOT_DELIVERED);
    } else if (saleOrderLine.getDeliveredQty().compareTo(saleOrderLine.getQty()) < 0) {
      saleOrderLine.setDeliveryState(SaleOrderLineRepository.DELIVERY_STATE_PARTIALLY_DELIVERED);
    } else {
      saleOrderLine.setDeliveryState(SaleOrderLineRepository.DELIVERY_STATE_DELIVERED);
    }
  }

  @Override
  public String getSaleOrderLineListForAProduct(
      Long productId, Long companyId, Long stockLocationId) {
    List<Integer> statusList = new ArrayList<>();
    statusList.add(SaleOrderRepository.STATUS_ORDER_CONFIRMED);
    String status =
        appSupplychainService.getAppSupplychain().getsOFilterOnStockDetailStatusSelect();
    if (!StringUtils.isBlank(status)) {
      statusList = StringHelper.getIntegerList(status);
    }
    String statusListQuery =
        statusList.stream().map(String::valueOf).collect(Collectors.joining(","));
    String query =
        "self.product.id = "
            + productId
            + " AND self.deliveryState != "
            + SaleOrderLineRepository.DELIVERY_STATE_DELIVERED
            + " AND self.saleOrder.statusSelect IN ("
            + statusListQuery
            + ")";

    if (companyId != 0L) {
      query += " AND self.saleOrder.company.id = " + companyId;
      if (stockLocationId != 0L) {
        StockLocation stockLocation =
            Beans.get(StockLocationRepository.class).find(stockLocationId);
        List<StockLocation> stockLocationList =
            Beans.get(StockLocationService.class)
                .getAllLocationAndSubLocation(stockLocation, false);
        if (!stockLocationList.isEmpty() && stockLocation.getCompany().getId().equals(companyId)) {
          query +=
              " AND self.saleOrder.stockLocation.id IN ("
                  + StringHelper.getIdListString(stockLocationList)
                  + ") ";
        }
      }
    }
    return query;
  }

  @Override
  public BigDecimal checkInvoicedOrDeliveredOrderQty(SaleOrderLine saleOrderLine) {
    BigDecimal qty = saleOrderLine.getQty();
    BigDecimal deliveredQty = saleOrderLine.getDeliveredQty();
    BigDecimal invoicedQty = getInvoicedQty(saleOrderLine);

    if (qty.compareTo(invoicedQty) < 0 && invoicedQty.compareTo(deliveredQty) > 0) {
      return invoicedQty;
    } else if (deliveredQty.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(deliveredQty) < 0) {
      return deliveredQty;
    }

    return qty;
  }

  @Transactional(rollbackOn = {Exception.class})
  public void updateStockMoveReservationDateTime(SaleOrderLine saleOrderLine)
      throws AxelorException {
    SaleOrder saleOrder = saleOrderLine.getSaleOrder();
    if (saleOrder == null) {
      return;
    }
    if (SupplyChainConfigRepository.SALE_ORDER_SHIPPING_DATE
        != Beans.get(SupplyChainConfigService.class)
            .getSupplyChainConfig(saleOrder.getCompany())
            .getSaleOrderReservationDateSelect()) {
      return;
    }

    Beans.get(StockMoveLineRepository.class)
        .all()
        .filter("self.saleOrderLine = :saleOrderLineId")
        .bind("saleOrderLineId", saleOrderLine.getId())
        .fetchStream()
        .filter(
            stockMoveLine ->
                stockMoveLine.getStockMove() != null
                    && stockMoveLine.getStockMove().getStatusSelect()
                        == StockMoveRepository.STATUS_PLANNED)
        .forEach(
            stockMoveLine ->
                stockMoveLine.setReservationDateTime(
                    saleOrderLine.getEstimatedShippingDate().atStartOfDay()));
  }

  @Override
  public Map<String, Object> updateRequestedReservedQty(SaleOrderLine saleOrderLine) {
    Map<String, Object> saleOrderLineMap = new HashMap<>();
    BigDecimal qty = saleOrderLine.getQty();
    BigDecimal reservedQty = saleOrderLine.getReservedQty();
    if (reservedQty.compareTo(qty) > 0 || saleOrderLine.getIsQtyRequested()) {
      saleOrderLine.setRequestedReservedQty(BigDecimal.ZERO.max(qty));
      saleOrderLineMap.put("requestedReservedQty", saleOrderLine.getRequestedReservedQty());
    }
    return saleOrderLineMap;
  }

  protected BigDecimal getInvoicedQty(SaleOrderLine saleOrderLine) {

    TypedQuery<BigDecimal> query =
        JPA.em()
            .createQuery(
                "SELECT COALESCE(SUM(CASE WHEN self.invoice.operationTypeSelect = 3 THEN self.qty WHEN self.invoice.operationTypeSelect = 4 THEN -self.qty END),0) FROM InvoiceLine self WHERE self.invoice.statusSelect = :statusSelect AND self.saleOrderLine.id = :saleOrderLineId",
                BigDecimal.class);
    query.setParameter("statusSelect", InvoiceRepository.STATUS_VENTILATED);
    query.setParameter("saleOrderLineId", saleOrderLine.getId());

    return query.getSingleResult();
  }
}
