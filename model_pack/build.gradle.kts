// OCR models, delivered with the app at install time (Play Asset Delivery).
// The base module must stay under Play's 200 MB download limit; the models alone are ~221 MB.
// Install-time packs are merged into the app's AssetManager, so code keeps using
// context.assets.open("models/...") unchanged.
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("model_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
