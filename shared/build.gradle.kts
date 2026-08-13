architectury {
    common("neoforge", "fabric")
}

dependencies {
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras)
    implementation(libs.geyser.api) // bundled so Hydraulic can load alongside plugin-based Geyser (Youer)
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    compileOnly("net.kyori:examination-api:1.3.0")
    compileOnly(libs.geyser.core) {
        exclude(group = "io.netty")
        exclude(group = "io.netty.incubator")
    }

    api(libs.pack.converter)

    implementation(libs.auto.service)
    annotationProcessor(libs.auto.service)

    // Only here to suppress "unknown enum constant EnvType.CLIENT" warnings.
    compileOnly(libs.fabric.loader)
}
