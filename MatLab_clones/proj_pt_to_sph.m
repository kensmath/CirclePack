function sz = proj_pt_to_sph(z)
%sz = proj_pt_to_sph(z) project pt in eucl plane to sphere (theta,phi)
%   Taken from java. Recall 0 goes to North pole. Things very
%   far from origin go to South pole.

zs=abs(z)^2;
if zs<.00000000001
    sz=0.0;
    return;
end

sz=atan2(imag(z),real(z))+acos((1-zs)/(1+zs))*1i;

end

