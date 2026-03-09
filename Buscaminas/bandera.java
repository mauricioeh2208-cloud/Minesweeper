public static VoxelShape makeShape() {
	return VoxelShapes.union(
		VoxelShapes.cuboid(0.4375, 0.125, 0.4375, 0.5625, 0.5625, 0.5625),
		VoxelShapes.cuboid(0.40625, 0.5625, 0.40625, 0.59375, 1.125, 0.59375),
		VoxelShapes.cuboid(-0.25, 0.53125, 0.5, 0.4375, 1.09375, 0.5),
		VoxelShapes.cuboid(0.375, 0, 0.375, 0.625, 0.125, 0.625)
	);
}