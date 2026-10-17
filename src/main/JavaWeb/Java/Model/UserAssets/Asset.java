package Model.UserAssets;

/**
 * 职责:
 * 提供资产的抽象
 * 每个资产都至少有币种和金额
 */
public interface Asset {

    java.lang.String getCurrency();
    java.lang.String getAmount();

}
