package com.rayneo.mathcosmos

import android.app.Application

/**
 * Nothing to set up.
 *
 * The sibling projects call MercurySDK.init here to register with the RayNeo compositor. This app
 * does not link that SDK: the only thing it needs from the platform is the `com.rayneo.mercury.app`
 * meta-data flag in the manifest, which is what tells the compositor to drive both lenses, and the
 * stereo itself is the app's own work — the renderer draws two eye viewports into one wide buffer
 * and BinocularSbsLayout mirrors every 2D overlay into both halves.
 *
 * Dropping the SDK removes the two vendor .aar files, which were the one part of this tree that
 * could not be redistributed. Verified on the glasses: both lenses still render.
 */
class MathCosmosApp : Application()
