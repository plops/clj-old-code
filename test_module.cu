extern "C" __global__ void Add(int *src, int delta)
{
        src[threadIdx.x] += delta;
}
