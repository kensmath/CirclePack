function sz = proj_vec_to_sph(x,y,z)
%sz = proj_vec_to_sph(x,y,z) project a 3D vector to sz on the sphere
%   sz is form (theta,phi). if the norm of vector is too small,
%   return 0.0 (the north pole).

vec=[x,y,z];
% default for things near origin 
S_TOLER=.0000000000001;
dist=norm(vec,2);
if dist<S_TOLER
    sz=0.0;
    return;
end

sz=atan2(vec(2),vec(1))+acos(vec(3)/dist)*1i;

end

